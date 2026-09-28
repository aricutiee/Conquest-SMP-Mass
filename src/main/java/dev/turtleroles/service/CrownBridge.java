package dev.turtleroles.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/** Reads the active holder from the supplied KingsCrown plugin. */
final class CrownBridge {
    private Plugin plugin;
    private Object manager;
    private Method isHolder;
    private boolean failed;

    boolean isHolder(Player player) {
        Plugin current = Bukkit.getPluginManager().getPlugin("KingsCrown");
        if (current == null || !current.isEnabled()) return false;
        if (current != plugin) {
            plugin = current;
            manager = null;
            isHolder = null;
            failed = false;
        }
        if (failed) return false;
        try {
            if (manager == null) {
                manager = current.getClass().getMethod("b").invoke(current);
                isHolder = manager.getClass().getMethod("f", Player.class);
            }
            return Boolean.TRUE.equals(isHolder.invoke(manager, player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            current.getLogger().warning("TurtleRoles could not read the KingsCrown holder: " + e.getMessage());
            return false;
        }
    }
}
