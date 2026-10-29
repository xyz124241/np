package de.neptun.pets;

import org.bukkit.Keyed;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Findet die Varianten eines Mobs automatisch per Reflection: jede setXxx(Wert)-Methode des
 * Mob-Interfaces (z. B. Axolotl.setVariant, Cat.setCatType, Horse.setColor), deren Parameter
 * ein Enum oder eine Registry-Konstantengruppe mit mehreren Werten ist.
 * So werden auch neue Varianten späterer Minecraft-Versionen ohne Codeänderung erkannt.
 */
final class Variants {

    record Prop(String name, Method setter, List<Object> values) {
    }

    private static final Set<String> BASE = Set.of("Entity", "LivingEntity", "Mob", "Ageable", "Breedable",
            "Damageable", "Attributable", "Tameable", "Animals", "Creature", "Sittable", "Steerable", "Lootable",
            "Vehicle", "Boss", "Raider", "Monster", "Spellcaster", "Golem", "Enemy", "AbstractVillager", "Fish",
            "WaterMob", "Flying");

    private static final Set<String> BAD_PARAM = Set.of("Pose", "Spell", "State", "Phase", "Material", "EntityType",
            "Color", "Location", "Vector", "GameMode", "EquipmentSlot", "Difficulty", "Sound", "Biome", "Particle",
            "Attribute", "Block", "BlockData", "World");

    private static final Map<Class<?>, List<Prop>> CACHE = new HashMap<>();

    private Variants() {
    }

    static synchronized List<Prop> props(Class<?> entityClass) {
        if (entityClass == null) {
            return List.of();
        }
        return CACHE.computeIfAbsent(entityClass, Variants::discover);
    }

    private static List<Prop> discover(Class<?> ec) {
        Map<String, Prop> out = new LinkedHashMap<>();
        for (Method m : ec.getMethods()) {
            String mn = m.getName();
            if (m.getParameterCount() != 1 || !mn.startsWith("set") || mn.length() < 4) {
                continue;
            }
            Class<?> owner = m.getDeclaringClass();
            if (!owner.getName().startsWith("org.bukkit.entity.") || BASE.contains(owner.getSimpleName())) {
                continue;
            }
            Class<?> pt = m.getParameterTypes()[0];
            if (pt.isPrimitive() || !pt.getName().startsWith("org.bukkit.") || BAD_PARAM.contains(pt.getSimpleName())) {
                continue;
            }
            List<Object> vals = valuesOf(pt);
            if (vals.size() < 2) {
                continue;
            }
            String n = Character.toLowerCase(mn.charAt(3)) + mn.substring(4);
            out.putIfAbsent(n, new Prop(n, m, vals));
        }
        return List.copyOf(out.values());
    }

    private static List<Object> valuesOf(Class<?> t) {
        List<Object> l = new ArrayList<>();
        if (t.isEnum()) {
            l.addAll(Arrays.asList(t.getEnumConstants()));
            return l;
        }
        for (Field f : t.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !t.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                Object v = f.get(null);
                if (v != null && !l.contains(v)) {
                    l.add(v);
                }
            } catch (Throwable ignored) {
                // Feld nicht lesbar - überspringen
            }
        }
        return l;
    }

    /** Lesbarer, kleingeschriebener Name eines Variantenwerts (z. B. "blue", "tabby"). */
    static String name(Object v) {
        if (v instanceof Enum<?> e) {
            return e.name().toLowerCase(Locale.ROOT);
        }
        if (v instanceof Keyed k) {
            try {
                String s = k.getKey().toString();
                return s.startsWith("minecraft:") ? s.substring(10) : s;
            } catch (Throwable ignored) {
                // weiter unten
            }
        }
        return String.valueOf(v).toLowerCase(Locale.ROOT);
    }

    static void apply(Entity e, String prop, Object value) {
        for (Prop p : props(e.getType().getEntityClass())) {
            if (p.name().equals(prop)) {
                try {
                    p.setter().invoke(e, value);
                } catch (Throwable ignored) {
                    // Variante nicht setzbar - Mob behält seinen Standard
                }
            }
        }
    }
}
