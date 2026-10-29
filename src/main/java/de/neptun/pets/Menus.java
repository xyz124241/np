package de.neptun.pets;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Alle Menüs: Mob-Liste mit Suche und der Editor (Variante / Effekte / Einstellungen / Name). */
final class Menus implements Listener {

    record Entry(EntityType type, String prop, Object value) {
    }

    static final class Gui implements InventoryHolder {
        enum Kind { LIST, EDITOR }

        final Kind kind;
        Inventory inv;

        Gui(Kind kind) {
            this.kind = kind;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    record Setting(int flag, String name, String desc) {
    }

    static final List<Setting> SETTINGS = List.of(
            new Setting(PetData.FOLLOW, "Folgt dir", "Läuft dir wie ein Hund hinterher."),
            new Setting(PetData.TELEPORT, "Teleportiert sich", "Teleportiert sich zu dir, wenn es zu weit weg ist."),
            new Setting(PetData.AGGRESSIVE, "Greift Spieler an", "Greift andere Spieler in der Nähe an. Aus = friedlich."),
            new Setting(PetData.MOVE, "Kann sich bewegen", "Aus = bleibt wie festgewurzelt stehen."),
            new Setting(PetData.LEASH, "Leine erlaubt", "Kann mit einer Leine geführt werden."),
            new Setting(PetData.FEED, "Andere dürfen füttern", "Andere Spieler können es mit Items füttern."),
            new Setting(PetData.HIT, "Andere dürfen schlagen", "Andere Spieler können es verletzen."),
            new Setting(PetData.PUSH, "Kann geschubst werden", "Kollision und Rückstoß durch andere."));
    private static final int[] SETTING_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};
    private static final int GROW_SLOT = 40;
    private static final Setting GROW_SETTING = new Setting(PetData.GROW, "Kann älter werden",
            "Aus = Alter eingefroren (Baby bleibt Baby).");
    private static final String AGE = "__age";
    private static final int[] AGE_TICKS = {0, -24000, -6000, -72000};
    private static final String[] AGE_NAMES = {"Erwachsen", "Baby (wächst in 20 min)", "Baby (wächst in 5 min)",
            "Baby (wächst in 60 min)"};
    private static final int GRID_EFFECT = 27;

    private final NeptunPets plugin;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();
    private static List<EntityType> mobCache;
    private static List<PotionEffectType> effectCache;

    Menus(NeptunPets plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Daten

    static synchronized List<EntityType> mobTypes() {
        if (mobCache == null) {
            List<EntityType> l = new ArrayList<>();
            for (EntityType t : EntityType.values()) {
                if (t == EntityType.UNKNOWN || t == EntityType.PLAYER || t == EntityType.ARMOR_STAND) {
                    continue;
                }
                Class<?> c = t.getEntityClass();
                if (c == null || !t.isSpawnable() || !LivingEntity.class.isAssignableFrom(c)) {
                    continue;
                }
                l.add(t);
            }
            l.sort(Comparator.comparing(EntityType::name));
            mobCache = l;
        }
        return mobCache;
    }

    static synchronized List<PotionEffectType> effects() {
        if (effectCache == null) {
            List<PotionEffectType> l = new ArrayList<>();
            Object reg = null;
            for (String fn : new String[]{"EFFECT", "POTION_EFFECT_TYPE"}) {
                try {
                    reg = Registry.class.getField(fn).get(null);
                    if (reg != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                    // nächster Name
                }
            }
            if (reg instanceof Iterable<?> it) {
                for (Object o : it) {
                    if (o instanceof PotionEffectType t) {
                        l.add(t);
                    }
                }
            }
            if (l.isEmpty()) {
                for (PotionEffectType t : PotionEffectType.values()) {
                    l.add(t);
                }
            }
            l.sort(Comparator.comparing(t -> t.getKey().getKey()));
            effectCache = l;
        }
        return effectCache;
    }

    static String mobText(EntityType t) {
        return (t.getKey().getKey() + " " + t.name()).toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    static List<Entry> entries(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        String[] tokens = q.isEmpty() ? new String[0] : q.split("\\s+");
        List<Entry> out = new ArrayList<>();
        for (EntityType t : mobTypes()) {
            String mt = mobText(t);
            if (containsAll(mt, tokens)) {
                out.add(new Entry(t, null, null));
                continue;
            }
            if (tokens.length == 0) {
                continue;
            }
            for (Variants.Prop p : Variants.props(t.getEntityClass())) {
                for (Object v : p.values()) {
                    String text = mt + " " + p.name().toLowerCase(Locale.ROOT) + " " + Variants.name(v);
                    if (containsAll(text, tokens)) {
                        out.add(new Entry(t, p.name(), v));
                    }
                }
            }
        }
        return out;
    }

    private static boolean containsAll(String text, String[] tokens) {
        for (String t : tokens) {
            if (!text.contains(t)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Item-Helfer

    static Component txt(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    static List<Component> lore(String... lines) {
        List<Component> l = new ArrayList<>();
        for (String s : lines) {
            l.add(txt(s, NamedTextColor.GRAY));
        }
        return l;
    }

    static ItemStack item(Material m, Component name, List<Component> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (Component c : lore) {
                l.add(c.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(l);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    static String pretty(String s) {
        String spaced = s.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (String w : spaced.split(" ")) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    static Material egg(EntityType t) {
        Material m = Material.matchMaterial(t.name() + "_SPAWN_EGG");
        return m != null ? m : Material.NAME_TAG;
    }

    static Component mobName(EntityType t) {
        return Component.translatable(t.translationKey());
    }

    private static Material valueIcon(Object v) {
        if (v instanceof DyeColor dc) {
            Material m = Material.matchMaterial(dc.name() + "_WOOL");
            if (m != null) {
                return m;
            }
        }
        return Material.PAPER;
    }

    private static ItemStack pane() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null, false);
    }

    // ------------------------------------------------------------------ Entwurf

    Draft draft(Player p) {
        return drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft());
    }

    /** Wird von /np benutzt: öffnet die Liste, oder direkt den Editor bei genau einem Treffer. */
    void search(Player p, String query) {
        List<Entry> found = entries(query);
        if (found.size() == 1) {
            select(p, found.get(0));
            openEditor(p);
        } else {
            openList(p, query);
        }
    }

    private void select(Player p, Entry en) {
        Draft d = draft(p);
        d.select(en.type());
        if (en.prop() != null) {
            d.variants.put(en.prop(), en.value());
            d.activeProp = en.prop();
        }
    }

    // ------------------------------------------------------------------ Liste

    void openList(Player p, String query) {
        Draft d = draft(p);
        d.query = query == null ? "" : query;
        d.listPage = 0;
        renderList(p);
    }

    private void renderList(Player p) {
        Draft d = draft(p);
        List<Entry> all = entries(d.query);
        int pages = Math.max(1, (all.size() + 44) / 45);
        d.listPage = Math.max(0, Math.min(d.listPage, pages - 1));
        d.shown = new ArrayList<>();

        Gui g = new Gui(Gui.Kind.LIST);
        String title = "Neptun Pets" + (d.query.isBlank() ? "" : " · " + d.query);
        Inventory inv = Bukkit.createInventory(g, 54, Component.text(title));
        g.inv = inv;

        for (int i = 0; i < 45; i++) {
            int idx = d.listPage * 45 + i;
            if (idx >= all.size()) {
                break;
            }
            Entry en = all.get(idx);
            d.shown.add(en);
            Component name = mobName(en.type()).color(NamedTextColor.WHITE);
            List<Component> lore = new ArrayList<>();
            if (en.prop() != null) {
                lore.add(txt(pretty(en.prop()) + ": " + Variants.name(en.value()), NamedTextColor.AQUA));
            }
            lore.add(txt("Klick: auswählen", NamedTextColor.GRAY));
            inv.setItem(i, item(egg(en.type()), name, lore, false));
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane());
        }
        if (d.listPage > 0) {
            inv.setItem(45, item(Material.ARROW, txt("Vorherige Seite", NamedTextColor.YELLOW), null, false));
        }
        inv.setItem(46, item(Material.PAPER, txt("Seite " + (d.listPage + 1) + "/" + pages, NamedTextColor.WHITE),
                lore(all.size() + " Treffer"), false));
        inv.setItem(47, item(Material.COMPASS, txt("Suchen", NamedTextColor.GREEN),
                lore("Mob oder Variante, z. B.", "\"blue axolotl\" oder \"tabby\".", "Auch per /np blue axolotl"), false));
        if (!d.query.isBlank()) {
            inv.setItem(48, item(Material.BARRIER, txt("Suche löschen", NamedTextColor.RED), null, false));
        }
        inv.setItem(49, item(Material.RED_DYE, txt("Schließen", NamedTextColor.RED), null, false));
        if (d.listPage < pages - 1) {
            inv.setItem(53, item(Material.ARROW, txt("Nächste Seite", NamedTextColor.YELLOW), null, false));
        }
        p.openInventory(inv);
    }

    private void clickList(Player p, Draft d, int slot) {
        if (slot < 45) {
            if (slot < d.shown.size()) {
                select(p, d.shown.get(slot));
                openEditor(p);
            }
            return;
        }
        switch (slot) {
            case 45 -> {
                d.listPage--;
                renderList(p);
            }
            case 53 -> {
                d.listPage++;
                renderList(p);
            }
            case 47 -> {
                p.closeInventory();
                searching.add(p.getUniqueId());
                p.sendMessage(Component.text("Schreibe den Suchbegriff in den Chat ", NamedTextColor.GREEN)
                        .append(Component.text("(abbrechen = abbrechen)", NamedTextColor.GRAY)));
            }
            case 48 -> openList(p, "");
            case 49 -> p.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!searching.remove(id)) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("abbrechen")) {
                renderList(p);
            } else {
                openList(p, text);
            }
        });
    }

    // ------------------------------------------------------------------ Editor

    void openEditor(Player p) {
        Draft d = drafts.get(p.getUniqueId());
        if (d == null || d.type == null) {
            openList(p, "");
            return;
        }
        Gui g = new Gui(Gui.Kind.EDITOR);
        Inventory inv = Bukkit.createInventory(g, 54, Component.text("Pet: ").append(mobName(d.type)));
        g.inv = inv;
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane());
        }

        inv.setItem(1, item(egg(d.type), txt("Variante", NamedTextColor.GOLD),
                lore("Farbe, Typ, Muster ..."), d.tab == Draft.Tab.VARIANT));
        inv.setItem(3, item(Material.BREWING_STAND, txt("Effekte", NamedTextColor.LIGHT_PURPLE),
                lore("Trankeffekte, Stärke, Dauer"), d.tab == Draft.Tab.EFFECTS));
        inv.setItem(5, item(Material.COMPARATOR, txt("Einstellungen", NamedTextColor.AQUA),
                lore("Folgen, Angriff, Leine ..."), d.tab == Draft.Tab.SETTINGS));
        List<Component> nameLore = new ArrayList<>();
        nameLore.add(txt(d.name == null ? "Aktuell: kein Name" : "Name gesetzt", NamedTextColor.GRAY));
        if (d.name != null) {
            nameLore.add(Component.empty().decoration(TextDecoration.ITALIC, false).append(d.name));
            nameLore.add(txt("Rechtsklick: Name entfernen", NamedTextColor.DARK_GRAY));
        }
        nameLore.add(txt("Klick: Amboss öffnen (MiniMessage)", NamedTextColor.GRAY));
        inv.setItem(7, item(Material.NAME_TAG, txt("Name", NamedTextColor.YELLOW), nameLore, d.name != null));

        inv.setItem(45, item(Material.ARROW, txt("Zurück zur Liste", NamedTextColor.YELLOW), null, false));
        inv.setItem(49, item(Material.LIME_CONCRETE, txt("Pet spawnen", NamedTextColor.GREEN),
                lore("Spawnt das Pet vor dir."), true));
        inv.setItem(53, item(Material.RED_DYE, txt("Schließen", NamedTextColor.RED), null, false));

        switch (d.tab) {
            case VARIANT -> renderVariant(inv, d);
            case EFFECTS -> renderEffects(inv, d);
            case SETTINGS -> renderSettings(inv, d);
        }
        p.openInventory(inv);
    }

    private static boolean ageable(Draft d) {
        Class<?> c = d.type.getEntityClass();
        return c != null && Ageable.class.isAssignableFrom(c);
    }

    private static String ageName(int ticks) {
        for (int i = 0; i < AGE_TICKS.length; i++) {
            if (AGE_TICKS[i] == ticks) {
                return AGE_NAMES[i];
            }
        }
        return AGE_NAMES[0];
    }

    /** Zeilen im Varianten-Tab: alle Varianten-Eigenschaften plus (bei Tieren) das Alter. */
    private static List<String> variantRows(Draft d) {
        List<String> rows = new ArrayList<>();
        for (Variants.Prop pr : Variants.props(d.type.getEntityClass())) {
            rows.add(pr.name());
        }
        if (ageable(d)) {
            rows.add(AGE);
        }
        return rows;
    }

    private void renderVariant(Inventory inv, Draft d) {
        List<String> rows = variantRows(d);
        if (rows.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, txt("Keine Varianten", NamedTextColor.RED),
                    lore("Dieser Mob hat keine einstellbaren", "Varianten."), false));
            return;
        }
        if (d.activeProp == null || !rows.contains(d.activeProp)) {
            d.activeProp = rows.get(0);
        }
        for (int i = 0; i < Math.min(rows.size(), 9); i++) {
            String r = rows.get(i);
            boolean on = r.equals(d.activeProp);
            boolean age = r.equals(AGE);
            String cur;
            if (age) {
                cur = ageName(d.babyTicks);
            } else {
                Object v = d.variants.get(r);
                cur = v == null ? "zufällig" : Variants.name(v);
            }
            inv.setItem(9 + i, item(age ? Material.WHEAT : Material.BOOK,
                    txt(age ? "Alter" : pretty(r), on ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                    lore("Aktuell: " + cur, "Klick: bearbeiten"), on));
        }
        if (AGE.equals(d.activeProp)) {
            for (int k = 0; k < AGE_TICKS.length; k++) {
                boolean sel = AGE_TICKS[k] == d.babyTicks;
                inv.setItem(18 + k, item(k == 0 ? Material.WHEAT : Material.EGG,
                        txt(AGE_NAMES[k], sel ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                        sel ? lore("Ausgewählt") : lore("Klick: auswählen"), sel));
            }
            return;
        }
        Variants.Prop ap = null;
        for (Variants.Prop pr : Variants.props(d.type.getEntityClass())) {
            if (pr.name().equals(d.activeProp)) {
                ap = pr;
            }
        }
        if (ap == null) {
            return;
        }
        Object selected = d.variants.get(ap.name());
        for (int j = 0; j < Math.min(ap.values().size(), 27); j++) {
            Object v = ap.values().get(j);
            boolean sel = v.equals(selected);
            inv.setItem(18 + j, item(valueIcon(v),
                    txt(pretty(Variants.name(v)), sel ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                    sel ? lore("Ausgewählt", "Nochmal klicken = zufällig") : lore("Klick: auswählen"), sel));
        }
    }

    private void renderEffects(Inventory inv, Draft d) {
        List<PotionEffectType> all = effects();
        int pages = Math.max(1, (all.size() + GRID_EFFECT - 1) / GRID_EFFECT);
        d.effPage = Math.max(0, Math.min(d.effPage, pages - 1));
        for (int i = 0; i < GRID_EFFECT; i++) {
            int idx = d.effPage * GRID_EFFECT + i;
            if (idx >= all.size()) {
                break;
            }
            PotionEffectType t = all.get(idx);
            int[] v = d.effects.get(t);
            ItemStack it = new ItemStack(Material.POTION);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.translatable(t.translationKey())
                    .color(v != null ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> l = new ArrayList<>();
            if (v != null) {
                l.add(txt("Aktiv: Stufe " + (v[0] + 1) + ", " + durationText(v[1]), NamedTextColor.GREEN));
                l.add(txt("Linksklick: entfernen", NamedTextColor.GRAY));
                l.add(txt("Rechtsklick: bearbeiten", NamedTextColor.GRAY));
            } else {
                l.add(txt("Linksklick: hinzufügen", NamedTextColor.GRAY));
            }
            meta.lore(l);
            if (meta instanceof PotionMeta pm) {
                try {
                    pm.setColor(t.getColor());
                } catch (Throwable ignored) {
                    // Farbe optional
                }
            }
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            if (t.equals(d.focus)) {
                meta.setEnchantmentGlintOverride(true);
            }
            it.setItemMeta(meta);
            inv.setItem(9 + i, it);
        }

        int[] f = d.focus == null ? null : d.effects.get(d.focus);
        inv.setItem(36, item(Material.RED_CONCRETE, txt("Stärke −", NamedTextColor.RED), lore("Klick: −1"), false));
        List<Component> info = new ArrayList<>();
        if (f == null) {
            info.add(txt("Wähle einen Effekt,", NamedTextColor.GRAY));
            info.add(txt("um Stärke und Dauer zu ändern.", NamedTextColor.GRAY));
        } else {
            info.add(txt("Stufe " + (f[0] + 1), NamedTextColor.WHITE));
            info.add(txt("Dauer: " + durationText(f[1]), NamedTextColor.WHITE));
        }
        inv.setItem(37, item(Material.PAPER, d.focus == null ? txt("Kein Effekt gewählt", NamedTextColor.GRAY)
                : Component.translatable(d.focus.translationKey()).color(NamedTextColor.GOLD), info, false));
        inv.setItem(38, item(Material.LIME_CONCRETE, txt("Stärke +", NamedTextColor.GREEN), lore("Klick: +1"), false));
        inv.setItem(39, item(Material.RED_CONCRETE, txt("Dauer −", NamedTextColor.RED),
                lore("Links: −10 s", "Rechts: −60 s"), false));
        inv.setItem(40, item(Material.LIME_CONCRETE, txt("Dauer +", NamedTextColor.GREEN),
                lore("Links: +10 s", "Rechts: +60 s"), false));
        inv.setItem(41, item(Material.CLOCK, txt("Unendlich an/aus", NamedTextColor.AQUA), null,
                f != null && f[1] < 0));
        inv.setItem(44, item(Material.BARRIER, txt("Alle Effekte entfernen", NamedTextColor.RED), null, false));
        if (d.effPage > 0) {
            inv.setItem(47, item(Material.ARROW, txt("Vorherige Seite", NamedTextColor.YELLOW), null, false));
        }
        if (d.effPage < pages - 1) {
            inv.setItem(51, item(Material.ARROW, txt("Nächste Seite", NamedTextColor.YELLOW), null, false));
        }
    }

    private static String durationText(int sec) {
        if (sec < 0) {
            return "unendlich";
        }
        if (sec >= 60) {
            return (sec / 60) + " min " + (sec % 60) + " s";
        }
        return sec + " s";
    }

    private void renderSettings(Inventory inv, Draft d) {
        for (int i = 0; i < SETTINGS.size(); i++) {
            Setting s = SETTINGS.get(i);
            boolean on = (d.flags & s.flag()) != 0;
            inv.setItem(SETTING_SLOTS[i], item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    txt(s.name(), on ? NamedTextColor.GREEN : NamedTextColor.RED),
                    lore(s.desc(), on ? "Status: an" : "Status: aus", "Klick: umschalten"), on));
        }
        if (ageable(d)) {
            boolean on = (d.flags & PetData.GROW) != 0;
            inv.setItem(GROW_SLOT, item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    txt(GROW_SETTING.name(), on ? NamedTextColor.GREEN : NamedTextColor.RED),
                    lore(GROW_SETTING.desc(), on ? "Status: an" : "Status: aus", "Klick: umschalten"), on));
        }
    }

    // ------------------------------------------------------------------ Klicks im Editor

    private void clickEditor(Player p, Draft d, int slot, ClickType click) {
        switch (slot) {
            case 1 -> {
                d.tab = Draft.Tab.VARIANT;
                openEditor(p);
                return;
            }
            case 3 -> {
                d.tab = Draft.Tab.EFFECTS;
                openEditor(p);
                return;
            }
            case 5 -> {
                d.tab = Draft.Tab.SETTINGS;
                openEditor(p);
                return;
            }
            case 7 -> {
                if (click.isRightClick()) {
                    d.name = null;
                    openEditor(p);
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.anvil().open(p, c -> {
                        d.name = c;
                        openEditor(p);
                    }, () -> openEditor(p)));
                }
                return;
            }
            case 45 -> {
                openList(p, d.query);
                return;
            }
            case 49 -> {
                spawn(p, d);
                return;
            }
            case 53 -> {
                p.closeInventory();
                return;
            }
            default -> {
            }
        }
        switch (d.tab) {
            case VARIANT -> clickVariant(p, d, slot);
            case EFFECTS -> clickEffects(p, d, slot, click);
            case SETTINGS -> clickSettings(p, d, slot);
        }
    }

    private void clickVariant(Player p, Draft d, int slot) {
        List<String> rows = variantRows(d);
        if (rows.isEmpty()) {
            return;
        }
        if (slot >= 9 && slot <= 17) {
            int i = slot - 9;
            if (i < rows.size()) {
                d.activeProp = rows.get(i);
                openEditor(p);
            }
            return;
        }
        if (slot < 18 || slot > 44) {
            return;
        }
        int j = slot - 18;
        if (AGE.equals(d.activeProp)) {
            if (j < AGE_TICKS.length) {
                d.babyTicks = AGE_TICKS[j];
                openEditor(p);
            }
            return;
        }
        Variants.Prop ap = null;
        for (Variants.Prop pr : Variants.props(d.type.getEntityClass())) {
            if (pr.name().equals(d.activeProp)) {
                ap = pr;
            }
        }
        if (ap != null && j < ap.values().size()) {
            Object v = ap.values().get(j);
            if (v.equals(d.variants.get(ap.name()))) {
                d.variants.remove(ap.name());
            } else {
                d.variants.put(ap.name(), v);
            }
            openEditor(p);
        }
    }

    private void clickEffects(Player p, Draft d, int slot, ClickType click) {
        List<PotionEffectType> all = effects();
        if (slot >= 9 && slot < 9 + GRID_EFFECT) {
            int idx = d.effPage * GRID_EFFECT + (slot - 9);
            if (idx >= all.size()) {
                return;
            }
            PotionEffectType t = all.get(idx);
            if (click.isRightClick()) {
                if (d.effects.containsKey(t)) {
                    d.focus = t;
                }
            } else if (d.effects.containsKey(t)) {
                d.effects.remove(t);
                if (t.equals(d.focus)) {
                    d.focus = null;
                }
            } else {
                d.effects.put(t, new int[]{0, 60});
                d.focus = t;
            }
            openEditor(p);
            return;
        }
        int[] f = d.focus == null ? null : d.effects.get(d.focus);
        boolean change = false;
        switch (slot) {
            case 36 -> {
                if (f != null) {
                    f[0] = Math.max(0, f[0] - 1);
                    change = true;
                }
            }
            case 38 -> {
                if (f != null) {
                    f[0] = Math.min(254, f[0] + 1);
                    change = true;
                }
            }
            case 39, 40 -> {
                if (f != null && f[1] >= 0) {
                    int step = click.isRightClick() ? 60 : 10;
                    f[1] = Math.max(1, f[1] + (slot == 40 ? step : -step));
                    change = true;
                }
            }
            case 41 -> {
                if (f != null) {
                    f[1] = f[1] < 0 ? 60 : -1;
                    change = true;
                }
            }
            case 44 -> {
                d.effects.clear();
                d.focus = null;
                change = true;
            }
            case 47 -> {
                d.effPage--;
                change = true;
            }
            case 51 -> {
                d.effPage++;
                change = true;
            }
            default -> {
            }
        }
        if (change) {
            openEditor(p);
        }
    }

    private void clickSettings(Player p, Draft d, int slot) {
        for (int i = 0; i < SETTINGS.size(); i++) {
            if (SETTING_SLOTS[i] == slot) {
                d.flags ^= SETTINGS.get(i).flag();
                openEditor(p);
                return;
            }
        }
        if (slot == GROW_SLOT && ageable(d)) {
            d.flags ^= PetData.GROW;
            openEditor(p);
        }
    }

    private void spawn(Player p, Draft d) {
        p.closeInventory();
        Entity e = plugin.pets().spawn(p, d);
        if (e == null) {
            p.sendMessage(Component.text("Das Pet konnte nicht gespawnt werden (von einem anderen Plugin blockiert?).",
                    NamedTextColor.RED));
        } else {
            p.sendMessage(Component.text("Pet gespawnt. ", NamedTextColor.GREEN)
                    .append(Component.text("Umbenennen: /np name, entfernen: /np remove (Pet anschauen).",
                            NamedTextColor.GRAY)));
        }
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui g) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        e.setCancelled(true);
        if (!p.isOp()) {
            p.closeInventory();
            return;
        }
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) {
            return;
        }
        Draft d = drafts.get(p.getUniqueId());
        if (d == null) {
            p.closeInventory();
            return;
        }
        if (g.kind == Gui.Kind.LIST) {
            clickList(p, d, e.getSlot());
        } else {
            clickEditor(p, d, e.getSlot(), e.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Gui) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        drafts.remove(e.getPlayer().getUniqueId());
        searching.remove(e.getPlayer().getUniqueId());
    }
}
