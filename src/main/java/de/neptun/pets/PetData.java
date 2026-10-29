package de.neptun.pets;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Daten, die direkt am Pet (Entity) gespeichert werden, damit sie Neustarts überleben. */
final class PetData {

    static final int FOLLOW = 1;
    static final int TELEPORT = 2;
    static final int AGGRESSIVE = 4;
    static final int MOVE = 8;
    static final int LEASH = 16;
    static final int FEED = 32;
    static final int HIT = 64;
    static final int PUSH = 128;
    /** Gesetzt = Baby darf erwachsen werden. Nicht gesetzt = Alter ist eingefroren. */
    static final int GROW = 256;
    static final int DEFAULT = FOLLOW | TELEPORT | MOVE | LEASH | PUSH | GROW;

    static NamespacedKey OWNER;
    static NamespacedKey FLAGS;

    private PetData() {
    }

    static void init(Plugin plugin) {
        OWNER = new NamespacedKey(plugin, "owner");
        FLAGS = new NamespacedKey(plugin, "flags");
    }

    static boolean isPet(Entity e) {
        return e != null && e.getPersistentDataContainer().has(OWNER, PersistentDataType.STRING);
    }

    static UUID owner(Entity e) {
        String s = e.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static int flags(Entity e) {
        Integer i = e.getPersistentDataContainer().get(FLAGS, PersistentDataType.INTEGER);
        return i == null ? DEFAULT : i;
    }

    static boolean has(Entity e, int flag) {
        return (flags(e) & flag) != 0;
    }

    static void write(Entity e, UUID owner, int flags) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(OWNER, PersistentDataType.STRING, owner.toString());
        pdc.set(FLAGS, PersistentDataType.INTEGER, flags);
    }
}
