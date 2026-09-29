package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Stored roles only: operator status and wildcard permissions do not grant this exemption. */
public final class GameplayBypass {
    private GameplayBypass() {}
    public static boolean role(Role role) {
        return role == Role.MODERATOR || role == Role.SSER || role == Role.ADMIN || role == Role.SR_ADMIN || role == Role.CO_OWNER || role == Role.OWNER;
    }
    public static boolean allowed(Plugin plugin, Entity actor) {
        return actor instanceof Player player && plugin instanceof TurtleRolesPlugin conquest
            && conquest.roleService() != null && role(conquest.roleService().roleOf(player.getUniqueId()));
    }
}
