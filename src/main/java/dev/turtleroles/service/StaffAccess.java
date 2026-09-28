package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.Set;
import java.util.UUID;

/** Role-managed operators retain their stored rank for target protection. */
public final class StaffAccess {
    private StaffAccess() {}
    public static boolean managed(Role role) {
        return role == Role.SSER || role == Role.ADMIN || role == Role.SR_ADMIN || role == Role.CO_OWNER;
    }
    public static Set<String> permissions(Role role) {
        if (!managed(role)) return Set.of();
        return Set.of("ss.use", "ss.vanish.see", "serverutil.use", "serverutil.players", "serverutil.players.heal",
                "serverutil.players.feed", "serverutil.players.effects", "serverutil.players.gamemode",
                "serverutil.players.inventory.view", "serverutil.players.enderchest.view",
                "serverutil.freeze", "serverutil.history", "serverutil.punishments",
                "serverutil.punishments.warn", "serverutil.punishments.tempmute", "serverutil.punishments.mute",
                "serverutil.punishments.tempban", "serverutil.punishments.ban",
                "serverutil.punishments.clearinventory", "serverutil.punishments.clearenderchest",
                "serverutil.reports.manage", "serverutil.announcement", "serverutil.graceperiod",
                "serverutil.vanish", "serverutil.staffmode", "serverutil.staffchat",
                "serverutil.chat.clear", "serverutil.chat.lock", "serverutil.chat.slowmode",
                "serverutil.chat.filter", "serverutil.chat.bypass", "serverutil.performance",
                "serverutil.audit", "shocksmp.events.admin", "shocksmp.events.kit", "shocksmp.events.reward");
    }
    public static boolean check(Plugin plugin, Player staff, UUID target, boolean selfAllowed) {
        if (!(plugin instanceof TurtleRolesPlugin combined)) return true;
        RoleService roles = combined.roleService();
        if (roles == null) return false;
        var actor = roles.actor(staff);
        boolean allowed = actor.ownerOverride() || (managed(actor.role()) &&
                (selfAllowed && staff.getUniqueId().equals(target) || actor.role().outranks(roles.effectiveRoleOf(target))));
        if (!allowed) staff.sendMessage("You can only manage players below your role.");
        return allowed;
    }
}
