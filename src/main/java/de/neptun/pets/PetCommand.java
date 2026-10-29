package de.neptun.pets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /np, /np &lt;Mob&gt; [Variante], /np name, /np remove - nur für OPs. */
final class PetCommand implements TabExecutor {

    private final NeptunPets plugin;

    PetCommand(NeptunPets plugin) {
        this.plugin = plugin;
    }

    private static void say(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text("[Pets] ", NamedTextColor.DARK_GRAY).append(Component.text(text, c)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            say(sender, "Nur Spieler können /np benutzen.", NamedTextColor.RED);
            return true;
        }
        if (!p.isOp()) {
            say(p, "Nur OPs dürfen Pets benutzen.", NamedTextColor.RED);
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openList(p, "");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && (sub.equals("name") || sub.equals("remove"))) {
            Entity t = p.getTargetEntity(8);
            if (!PetData.isPet(t)) {
                say(p, "Schau ein Pet an (max. 8 Blöcke Abstand).", NamedTextColor.RED);
                return true;
            }
            if (sub.equals("remove")) {
                plugin.pets().kill(t);
                say(p, "Pet entfernt.", NamedTextColor.GREEN);
            } else {
                plugin.anvil().open(p, c -> {
                    if (t.isValid()) {
                        t.customName(c);
                        t.setCustomNameVisible(true);
                        say(p, "Name gesetzt.", NamedTextColor.GREEN);
                    }
                }, () -> {
                });
            }
            return true;
        }
        plugin.menus().search(p, String.join(" ", args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p) || !p.isOp() || args.length == 0) {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        if (args.length == 1) {
            out.add("name");
            out.add("remove");
        }
        for (EntityType t : Menus.mobTypes()) {
            out.add(t.getKey().getKey());
            if (args.length >= 2 && args[0].equalsIgnoreCase(t.getKey().getKey())) {
                for (Variants.Prop pr : Variants.props(t.getEntityClass())) {
                    for (Object v : pr.values()) {
                        out.add(Variants.name(v));
                    }
                }
            }
        }
        List<String> res = new ArrayList<>();
        for (String s : out) {
            if (s.startsWith(last)) {
                res.add(s);
            }
        }
        return res;
    }
}
