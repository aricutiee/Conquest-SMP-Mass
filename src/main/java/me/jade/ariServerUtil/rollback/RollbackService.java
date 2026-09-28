package me.jade.ariServerUtil.rollback;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RollbackService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;

    public RollbackService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeOldSnapshots, 20L * 600L, 20L * 600L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        saveSnapshot(player, "death", event.getDeathMessage(), event.getKeepInventory());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        database.query(conn -> {
            try (var ps = conn.prepareStatement("SELECT id,snapshot_id FROM queued_rollbacks WHERE target_uuid=? AND applied=0 ORDER BY created_at ASC LIMIT 1")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new long[]{rs.getLong(1), rs.getLong(2)};
                }
            }
        }).thenAccept(ids -> {
            if (ids != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> applySnapshot(event.getPlayer(), ids[1], true, ids[0]));
            }
        });
    }

    public void saveSnapshot(Player player, String kind, String cause, boolean keepInventory) {
        Location location = player.getLocation();
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT INTO snapshots(target_uuid,target_name,kind,inventory,armor,ender,level,exp,world,x,y,z,cause,keep_inventory,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, kind);
                ps.setString(4, Items.serialize(player.getInventory().getStorageContents()));
                ps.setString(5, Items.serialize(player.getInventory().getArmorContents()));
                ps.setString(6, Items.serialize(player.getEnderChest().getContents()));
                ps.setInt(7, player.getLevel());
                ps.setFloat(8, player.getExp());
                ps.setString(9, location.getWorld().getName());
                ps.setDouble(10, location.getX());
                ps.setDouble(11, location.getY());
                ps.setDouble(12, location.getZ());
                ps.setString(13, cause == null ? "" : cause);
                ps.setInt(14, keepInventory ? 1 : 0);
                ps.setLong(15, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
        });
    }

    public void queueOrApply(Player staff, UUID targetUuid, String targetName, long snapshotId) {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            saveSnapshot(target, "safety-before-rollback", "Before rollback " + snapshotId, false);
            applySnapshot(target, snapshotId, false, 0);
            audit.record(staff, "ROLLBACK_APPLY", targetUuid, targetName, "snapshot " + snapshotId, "success", Long.toString(snapshotId));
            return;
        }
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT INTO queued_rollbacks(snapshot_id,target_uuid,queued_by_uuid,queued_by_name,created_at,applied) VALUES(?,?,?,?,?,0)")) {
                ps.setLong(1, snapshotId);
                ps.setString(2, targetUuid.toString());
                ps.setString(3, staff.getUniqueId().toString());
                ps.setString(4, staff.getName());
                ps.setLong(5, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
        });
        audit.record(staff, "ROLLBACK_QUEUE", targetUuid, targetName, "snapshot " + snapshotId, "queued", Long.toString(snapshotId));
    }

    public void applySnapshot(Player target, long snapshotId, boolean queued, long queueId) {
        database.query(conn -> {
            try (var ps = conn.prepareStatement("SELECT inventory,armor,level,exp FROM snapshots WHERE id=? AND applied=0")) {
                ps.setLong(1, snapshotId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new Snapshot(Items.deserialize(rs.getString(1)), Items.deserialize(rs.getString(2)), rs.getInt(3), rs.getFloat(4));
                }
            }
        }).thenAccept(snapshot -> {
            if (snapshot == null) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                target.getInventory().clear();
                target.getInventory().setStorageContents(snapshot.inventory);
                target.getInventory().setArmorContents(snapshot.armor);
                target.setLevel(snapshot.level);
                target.setExp(snapshot.exp);
                database.execute(conn -> {
                    try (var ps = conn.prepareStatement("UPDATE snapshots SET applied=1 WHERE id=?")) {
                        ps.setLong(1, snapshotId);
                        ps.executeUpdate();
                    }
                    if (queued) {
                        try (var ps = conn.prepareStatement("UPDATE queued_rollbacks SET applied=1, applied_at=? WHERE id=? AND applied=0")) {
                            ps.setLong(1, Instant.now().toEpochMilli());
                            ps.setLong(2, queueId);
                            ps.executeUpdate();
                        }
                    }
                });
            });
        });
    }

    public java.util.concurrent.CompletableFuture<List<String>> snapshotSummaries(UUID target) {
        return database.query(conn -> {
            List<String> lines = new ArrayList<>();
            try (var ps = conn.prepareStatement("SELECT id,created_at,world,x,y,z,cause,level FROM snapshots WHERE target_uuid=? ORDER BY created_at DESC LIMIT 100")) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lines.add("#" + rs.getLong(1) + " " + Instant.ofEpochMilli(rs.getLong(2)) + " " + rs.getString(3) + " " + rs.getInt(4) + "," + rs.getInt(5) + "," + rs.getInt(6) + " xp " + rs.getInt(8) + " " + rs.getString(7));
                    }
                }
            }
            return lines;
        });
    }

    private void purgeOldSnapshots() {
        int keep = config.integer("rollbacks.max-snapshots-per-player", 25, 1, 500);
        long retention = Instant.now().minus(config.duration("rollbacks.retention", "30d")).toEpochMilli();
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("DELETE FROM snapshots WHERE created_at<? AND id NOT IN (SELECT id FROM snapshots s WHERE (SELECT count(*) FROM snapshots newer WHERE newer.target_uuid=s.target_uuid AND newer.created_at>=s.created_at)<=?)")) {
                ps.setLong(1, retention);
                ps.setInt(2, keep);
                ps.executeUpdate();
            }
        });
    }

    private record Snapshot(ItemStack[] inventory, ItemStack[] armor, int level, float exp) {
    }
}
