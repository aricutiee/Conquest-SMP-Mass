package dev.biomeraces;

import org.bukkit.plugin.java.JavaPlugin;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Loads the real race module against Conquest's actual command and permission manifest. */
public class RaceTestHost extends JavaPlugin {
    RaceModule races;
    @Override public void onEnable() { races = new RaceModule(this); races.enable(); }
    @Override public void onDisable() { if (races != null) races.close(); }
    static RaceTestHost load() { return MockBukkit.loadWith(RaceTestHost.class, RaceTestHost.class.getResourceAsStream("/plugin.yml")); }
}
