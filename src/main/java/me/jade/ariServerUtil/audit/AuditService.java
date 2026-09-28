package me.jade.ariServerUtil.audit;

import me.jade.ariServerUtil.persistence.Database;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AuditService {
    private final Database database;

    public AuditService(Database database) {
        this.database = database;
    }

    public void record(CommandSender staff, String action, UUID targetUuid, String targetName, String reason, String result, String relatedId) {
        UUID staffUuid = staff instanceof Player player ? player.getUniqueId() : null;
        String staffName = staff instanceof Player player ? player.getName() : "Console";
        database.audit(staffUuid, staffName, action, targetUuid, targetName, reason, result, relatedId);
        database.history(targetUuid, targetName, staffUuid, staffName, action, reason + " | " + result, relatedId);
    }
}
