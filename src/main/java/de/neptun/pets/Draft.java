package de.neptun.pets;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Der Entwurf eines Pets, den ein OP gerade im Menü zusammenstellt. */
final class Draft {

    enum Tab { VARIANT, EFFECTS, SETTINGS }

    // Listen-Ansicht
    String query = "";
    int listPage;
    List<Menus.Entry> shown = new ArrayList<>();

    // Editor
    EntityType type;
    Tab tab = Tab.VARIANT;
    String activeProp;
    final Map<String, Object> variants = new LinkedHashMap<>();
    /** Effekt -> {Stärke (0 = Level 1), Dauer in Sekunden, -1 = unendlich} */
    final Map<PotionEffectType, int[]> effects = new LinkedHashMap<>();
    PotionEffectType focus;
    int effPage;
    int flags = PetData.DEFAULT;
    /** 0 = erwachsen, negativ = Baby, Betrag = Ticks bis zum Erwachsenwerden. */
    int babyTicks;
    Component name;

    void select(EntityType t) {
        type = t;
        tab = Tab.VARIANT;
        activeProp = null;
        variants.clear();
        effects.clear();
        focus = null;
        effPage = 0;
        flags = PetData.DEFAULT;
        babyTicks = 0;
        name = null;
    }
}
