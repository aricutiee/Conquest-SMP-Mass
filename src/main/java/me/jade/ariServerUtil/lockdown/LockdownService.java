package me.jade.ariServerUtil.lockdown;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

public final class LockdownService implements Listener {
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private volatile boolean locked;
    private volatile String reason = "Maintenance";

    public LockdownService(ServerUtilConfig config, Database database, AuditService audit) {
        this.config = config;
        this.database = database;
        this.audit = audit;
        load();
    }

    public boolean locked() {
        return locked;
    }

    public String reason() {
        return reason;
    }

    public void set(Player staff, boolean locked, String reason, boolean kickOnline) {
        this.locked = locked;
        this.reason = reason == null || reason.isBlank() ? "Maintenance" : reason;
        save();
        Bukkit.broadcast(Text.mm((locked ? config.message("lockdown.enabled") : config.message("lockdown.disabled")).replace("<reason>", this.reason).replace("<staff>", staff.getName())));
        if (locked && kickOnline) {
            Bukkit.getOnlinePlayers().stream()
                    .filter(player -> !player.isOp() && !player.hasPermission(Permissions.LOCKDOWN_BYPASS))
                    .forEach(player -> player.kick(Text.mm(config.message("lockdown.kick").replace("<reason>", this.reason))));
        }
        audit.record(staff, locked ? "LOCKDOWN_ENABLE" : "LOCKDOWN_DISABLE", null, "", this.reason, "success", "");
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (!locked) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission(Permissions.LOCKDOWN_BYPASS)) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Text.mm(config.message("lockdown.login").replace("<reason>", reason)));
    }

    private void save() {
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO state(key,value) VALUES('lockdown_locked',?),('lockdown_reason',?)")) {
                ps.setString(1, Boolean.toString(locked));
                ps.setString(2, reason);
                ps.executeUpdate();
            }
        });
    }

    private void load() {
        try {
            var values = database.query(conn -> {
                String lockedValue = "false";
                String reasonValue = "Maintenance";
                try (var ps = conn.prepareStatement("SELECT key,value FROM state WHERE key IN('lockdown_locked','lockdown_reason')")) {
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (rs.getString(1).equals("lockdown_locked")) {
                                lockedValue = rs.getString(2);
                            } else {
                                reasonValue = rs.getString(2);
                            }
                        }
                    }
                }
                return java.util.List.of(lockedValue, reasonValue);
            }).join();
            locked = Boolean.parseBoolean(values.get(0));
            reason = values.get(1);
        } catch (Exception ignored) {
        }
    }
}
