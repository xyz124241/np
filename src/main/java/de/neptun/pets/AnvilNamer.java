package de.neptun.pets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Amboss-Menü zum Benennen: Links liegt ein Papier (nicht entnehmbar), man tippt den Namen
 * mit MiniMessage ein ("<gradient:red:gold>Bello</gradient>"), rechts erscheint ein Name Tag.
 * Klick auf den Name Tag übernimmt den Namen - es wird nie ein Item ausgegeben.
 */
final class AnvilNamer implements Listener {

    private static final class Session {
        final Consumer<Component> done;
        final Runnable cancel;
        Component result;

        Session(Consumer<Component> done, Runnable cancel) {
            this.done = done;
            this.cancel = cancel;
        }
    }

    private final NeptunPets plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    AnvilNamer(NeptunPets plugin) {
        this.plugin = plugin;
    }

    void open(Player p, Consumer<Component> done, Runnable cancel) {
        try {
            AnvilView view = MenuType.ANVIL.builder()
                    .title(Component.text("Name (MiniMessage)"))
                    .checkReachable(false)
                    .build(p);
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.displayName(Component.text("Name hier eingeben", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("MiniMessage erlaubt, z. B.", NamedTextColor.DARK_GRAY),
                    Component.text("<red>Bello</red>  <rainbow>Max</rainbow>", NamedTextColor.DARK_GRAY)));
            paper.setItemMeta(meta);
            view.getTopInventory().setItem(0, paper);
            sessions.put(p.getUniqueId(), new Session(done, cancel));
            p.openInventory(view);
        } catch (Throwable t) {
            sessions.remove(p.getUniqueId());
            plugin.getLogger().warning("Amboss-Menü konnte nicht geöffnet werden: " + t);
            p.sendMessage(Component.text("Das Amboss-Menü ist auf dieser Server-Version nicht verfügbar.",
                    NamedTextColor.RED));
            cancel.run();
        }
    }

    private static Component parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MiniMessage.miniMessage().deserialize(text);
        } catch (Throwable t) {
            return Component.text(text);
        }
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!sessions.containsKey(e.getView().getPlayer().getUniqueId())) {
            return;
        }
        AnvilView view = e.getView();
        Component c = parse(view.getRenameText());
        if (c == null) {
            e.setResult(null);
            return;
        }
        ItemStack tag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = tag.getItemMeta();
        meta.displayName(c.decoration(TextDecoration.ITALIC, false));
        tag.setItemMeta(meta);
        e.setResult(tag);
        view.setRepairCost(0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Session s = sessions.get(e.getWhoClicked().getUniqueId());
        if (s == null || e.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        e.setCancelled(true); // nichts darf rein oder raus (Papier bleibt im Menü)
        if (e.getRawSlot() != 2 || !(e.getView() instanceof AnvilView view)) {
            return;
        }
        Component c = parse(view.getRenameText());
        if (c == null) {
            return;
        }
        s.result = c;
        Player p = (Player) e.getWhoClicked();
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (sessions.containsKey(e.getWhoClicked().getUniqueId())
                && e.getView().getTopInventory().getType() == InventoryType.ANVIL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Session s = sessions.get(id);
        if (s == null || !(e.getInventory() instanceof AnvilInventory)) {
            return;
        }
        sessions.remove(id);
        e.getInventory().clear(); // Papier verschwindet, wird nicht ins Inventar gelegt
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (s.result != null) {
                s.done.accept(s.result);
            } else {
                s.cancel.run();
            }
        });
    }
}
