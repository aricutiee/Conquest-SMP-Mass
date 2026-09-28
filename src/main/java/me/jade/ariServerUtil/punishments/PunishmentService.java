package me.jade.ariServerUtil.punishments;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.model.PunishmentRecord;
import me.jade.ariServerUtil.model.PunishmentType;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.DurationParser;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PunishmentService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;

    public PunishmentService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        Bukkit.getScheduler().runTaskTimer(plugin, this::expirePunishments, 20L * 60L, 20L * 60L);
    }

    public void punish(Player staff, UUID targetUuid, String targetName, PunishmentType type, Duration duration, String reason, Long reportId) {
        if (!dev.turtleroles.service.StaffAccess.check(plugin, staff, targetUuid, false)) return;
        if (isProtected(targetUuid) && !staff.hasPermission(Permissions.OVERRIDE_PROTECTED)) {
            Text.send(staff, config.message("punishments.protected"));
            audit.record(staff, "PUNISHMENT_DENIED", targetUuid, targetName, reason, "protected", reportId == null ? "" : reportId.toString());
            return;
        }
        String finalReason = reason == null || reason.isBlank() ? config.string("punishments.default-reason", "No reason provided") : reason;
        Instant now = Instant.now();
        Instant expires = duration == null ? null : now.plus(duration);
        database.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO punishments(target_uuid,target_name,staff_uuid,staff_name,type,reason,created_at,expires_at,active,related_report_id) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, targetName);
                ps.setString(3, staff.getUniqueId().toString());
                ps.setString(4, staff.getName());
                ps.setString(5, type.name());
                ps.setString(6, finalReason);
                ps.setLong(7, now.toEpochMilli());
                if (expires == null) {
                    ps.setObject(8, null);
                } else {
                    ps.setLong(8, expires.toEpochMilli());
                }
                ps.setInt(9, 1);
                ps.setObject(10, reportId);
                ps.executeUpdate();
            }
        });
        Player target = Bukkit.getPlayer(targetUuid);
        if (type == PunishmentType.CLEAR_INVENTORY || type == PunishmentType.CLEAR_ENDER_CHEST) {
            if (target == null) {
                Text.send(staff, "<red>The target must be online for that action.");
                return;
            }
            if (type == PunishmentType.CLEAR_INVENTORY) {
                target.getInventory().clear();
            } else {
                target.getEnderChest().clear();
            }
        }
        if ((type == PunishmentType.BAN || type == PunishmentType.TEMP_BAN) && target != null) {
            target.kick(Text.mm(config.message("punishments.ban-kick").replace("<reason>", finalReason)));
        }
        broadcast(type, targetName, staff.getName(), duration, finalReason);
        audit.record(staff, "PUNISHMENT_" + type.name(), targetUuid, targetName, finalReason, "success", reportId == null ? "" : reportId.toString());
    }

    public CompletableFuture<List<PunishmentRecord>> activePunishments(UUID target) {
        return database.query(conn -> {
            List<PunishmentRecord> records = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id,target_uuid,target_name,staff_uuid,staff_name,type,reason,created_at,expires_at,active,related_report_id FROM punishments WHERE target_uuid=? AND active=1 ORDER BY created_at DESC")) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(readPunishment(rs));
                    }
                }
            }
            return records;
        });
    }

    public Optional<PunishmentRecord> activeBanBlocking(UUID target) {
        try {
            return database.query(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT id,target_uuid,target_name,staff_uuid,staff_name,type,reason,created_at,expires_at,active,related_report_id FROM punishments WHERE target_uuid=? AND active=1 AND type IN('BAN','TEMP_BAN') ORDER BY created_at DESC LIMIT 1")) {
                    ps.setString(1, target.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.<PunishmentRecord>empty();
                        }
                        PunishmentRecord record = readPunishment(rs);
                        if (record.isExpired(Instant.now())) {
                            revokeExpired(record.id());
                            return Optional.<PunishmentRecord>empty();
                        }
                        return Optional.of(record);
                    }
                }
            }).join();
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not check ban for " + target + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    public void revoke(Player staff, long id) {
        database.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE punishments SET active=0, revoked_at=?, revoked_by_uuid=?, revoked_by_name=? WHERE id=? AND active=1")) {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, staff.getUniqueId().toString());
                ps.setString(3, staff.getName());
                ps.setLong(4, id);
                ps.executeUpdate();
            }
        });
        audit.record(staff, "PUNISHMENT_REVOKE", null, "", "id " + id, "success", Long.toString(id));
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Optional<PunishmentRecord> ban = activeBanBlocking(event.getUniqueId());
        ban.ifPresent(record -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Text.mm(config.message("punishments.login-ban").replace("<reason>", record.reason()))));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        try {
            boolean muted = activePunishments(uuid).join().stream()
                    .filter(PunishmentRecord::isMute)
                    .anyMatch(record -> !record.isExpired(Instant.now()));
            if (muted) {
                event.setCancelled(true);
                Text.send(event.getPlayer(), config.message("punishments.muted-chat"));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Mute check failed: " + ex.getMessage());
        }
    }

    private boolean isProtected(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.hasPermission(Permissions.PROTECTED);
    }

    private void expirePunishments() {
        database.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE punishments SET active=0, revoked_at=? WHERE active=1 AND expires_at IS NOT NULL AND expires_at<=?")) {
                long now = Instant.now().toEpochMilli();
                ps.setLong(1, now);
                ps.setLong(2, now);
                ps.executeUpdate();
            }
        });
    }

    private void revokeExpired(long id) {
        database.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE punishments SET active=0, revoked_at=? WHERE id=?")) {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        });
    }

    private PunishmentRecord readPunishment(ResultSet rs) throws Exception {
        long expires = rs.getLong("expires_at");
        return new PunishmentRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getString("target_name"),
                UUID.fromString(rs.getString("staff_uuid")),
                rs.getString("staff_name"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.wasNull() ? null : Instant.ofEpochMilli(expires),
                rs.getInt("active") == 1,
                rs.getObject("related_report_id") == null ? null : rs.getLong("related_report_id")
        );
    }

    private void broadcast(PunishmentType type, String target, String staff, Duration duration, String reason) {
        String message = switch (type) {
            case WARNING -> config.message("punishments.broadcast.warning");
            case TEMP_MUTE -> config.message("punishments.broadcast.temp-mute").replace("<duration>", DurationParser.human(duration));
            case MUTE -> config.message("punishments.broadcast.mute");
            case TEMP_BAN -> config.message("punishments.broadcast.temp-ban").replace("<duration>", DurationParser.human(duration));
            case BAN -> config.message("punishments.broadcast.ban");
            case CLEAR_INVENTORY -> config.message("punishments.broadcast.clear-inventory");
            case CLEAR_ENDER_CHEST -> config.message("punishments.broadcast.clear-ender");
        };
        String finalMessage = message.replace("<player>", target).replace("<staff>", staff).replace("<reason>", reason);
        Bukkit.getOnlinePlayers().forEach(player -> Text.send(player, finalMessage));
    }
}
