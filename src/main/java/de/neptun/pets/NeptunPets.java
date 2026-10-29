package de.neptun.pets;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** NeptunPets: Pets für OPs. Verschwinden automatisch, wenn der Besitzer kein OP mehr ist. */
public final class NeptunPets extends JavaPlugin {

    private PetManager pets;
    private Menus menus;
    private AnvilNamer anvil;

    @Override
    public void onEnable() {
        PetData.init(this);
        pets = new PetManager(this);
        anvil = new AnvilNamer(this);
        menus = new Menus(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(pets, this);
        pm.registerEvents(anvil, this);
        pm.registerEvents(menus, this);
        try {
            pm.registerEvents(new PushListener(), this);
        } catch (Throwable t) {
            getLogger().warning("Schubsen-Einstellung nicht verfügbar auf dieser Version: " + t);
        }

        PluginCommand np = getCommand("np");
        if (np != null) {
            PetCommand handler = new PetCommand(this);
            np.setExecutor(handler);
            np.setTabCompleter(handler);
        }
        pets.start();
        getLogger().info("NeptunPets aktiv.");
    }

    PetManager pets() {
        return pets;
    }

    Menus menus() {
        return menus;
    }

    AnvilNamer anvil() {
        return anvil;
    }
}
