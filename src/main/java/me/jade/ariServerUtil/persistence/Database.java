package me.jade.ariServerUtil.persistence;

import me.jade.ariServerUtil.model.PunishmentRecord;
import me.jade.ariServerUtil.model.PunishmentType;
import me.jade.ariServerUtil.model.ReportRecord;
import me.jade.ariServerUtil.model.ReportStatus;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Database {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ServerUtil-SQLite");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public Database(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
    }

    public void open() throws SQLException {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new SQLException("Could not create plugin data folder");
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "serverutil.db").getAbsolutePath());
        initialize(connection);
    }

    static void initialize(Connection connection) throws SQLException {
        // journal_mode returns a result set. Close it before executing another
        // statement, otherwise SQLite cannot finish its implicit transaction.
        try (Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("PRAGMA journal_mode=WAL")) {
                if (!result.next() || !"wal".equalsIgnoreCase(result.getString(1))) {
                    throw new SQLException("Could not enable SQLite WAL journal mode");
                }
            }
            statement.execute("PRAGMA foreign_keys=ON");
        }
        migrate(connection);
    }

    public void close() {
        executor.shutdown();
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not close SQLite connection: " + ex.getMessage());
        }
    }

    public CompletableFuture<Void> execute(ThrowingConsumer<Connection> action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.accept(connection);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, executor).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Database operation failed: " + error.getMessage());
            }
        });
    }

    public <T> CompletableFuture<T> query(ThrowingFunction<Connection, T> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.apply(connection);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, executor).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Database query failed: " + error.getMessage());
            }
        });
    }

    public void rememberPlayer(UUID uuid, String name, String address) {
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO players(uuid, latest_name, names, last_ip, first_seen, last_seen)
                    VALUES(?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                    latest_name=excluded.latest_name,
                    names=CASE WHEN instr(players.names, excluded.latest_name) = 0 THEN players.names || ',' || excluded.latest_name ELSE players.names END,
                    last_ip=excluded.last_ip,
                    last_seen=excluded.last_seen
                    """)) {
                long now = Instant.now().toEpochMilli();
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, name);
                ps.setString(4, address == null ? "" : address);
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<PlayerIdentity>> findPlayer(String input) {
        return query(conn -> {
            UUID uuid = null;
            try {
                uuid = UUID.fromString(input);
            } catch (IllegalArgumentException ignored) {
            }
            String sql = uuid == null
                    ? "SELECT uuid, latest_name, last_ip, first_seen, last_seen FROM players WHERE lower(latest_name)=lower(?) OR lower(names) LIKE lower(?) LIMIT 1"
                    : "SELECT uuid, latest_name, last_ip, first_seen, last_seen FROM players WHERE uuid=? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (uuid == null) {
                    ps.setString(1, input);
                    ps.setString(2, "%" + input + "%");
                } else {
                    ps.setString(1, uuid.toString());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new PlayerIdentity(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)));
                }
            }
        });
    }

    public void audit(UUID staffUuid, String staffName, String action, UUID targetUuid, String targetName, String reason, String result, String relatedId) {
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO audit(timestamp, staff_uuid, staff_name, action, target_uuid, target_name, reason, result, related_id) VALUES(?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, staffUuid == null ? "" : staffUuid.toString());
                ps.setString(3, staffName == null ? "Console" : staffName);
                ps.setString(4, action);
                ps.setString(5, targetUuid == null ? "" : targetUuid.toString());
                ps.setString(6, targetName == null ? "" : targetName);
                ps.setString(7, reason == null ? "" : reason);
                ps.setString(8, result == null ? "" : result);
                ps.setString(9, relatedId == null ? "" : relatedId);
                ps.executeUpdate();
            }
        });
    }

    public void history(UUID targetUuid, String targetName, UUID staffUuid, String staffName, String type, String detail, String relatedId) {
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO history(timestamp, target_uuid, target_name, staff_uuid, staff_name, type, detail, related_id) VALUES(?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, Instant.now().toEpochMilli());
                ps.setString(2, targetUuid == null ? "" : targetUuid.toString());
                ps.setString(3, targetName == null ? "" : targetName);
                ps.setString(4, staffUuid == null ? "" : staffUuid.toString());
                ps.setString(5, staffName == null ? "Console" : staffName);
                ps.setString(6, type);
                ps.setString(7, detail == null ? "" : detail);
                ps.setString(8, relatedId == null ? "" : relatedId);
                ps.executeUpdate();
            }
        });
    }

    private static void migrate(Connection connection) throws SQLException {
        List<String> statements = List.of(
                "CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)",
                "INSERT INTO schema_version SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM schema_version)",
                "CREATE TABLE IF NOT EXISTS players(uuid TEXT PRIMARY KEY, latest_name TEXT NOT NULL, names TEXT NOT NULL, last_ip TEXT, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS punishments(id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid TEXT NOT NULL, target_name TEXT NOT NULL, staff_uuid TEXT NOT NULL, staff_name TEXT NOT NULL, type TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER, active INTEGER NOT NULL, revoked_at INTEGER, revoked_by_uuid TEXT, revoked_by_name TEXT, related_report_id INTEGER)",
                "CREATE INDEX IF NOT EXISTS idx_punishments_target ON punishments(target_uuid, active)",
                "CREATE TABLE IF NOT EXISTS reports(id INTEGER PRIMARY KEY AUTOINCREMENT, reporter_uuid TEXT NOT NULL, reporter_name TEXT NOT NULL, reported_uuid TEXT NOT NULL, reported_name TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL, status TEXT NOT NULL, assigned_staff_uuid TEXT, staff_notes TEXT, resolution_time INTEGER, resolution_result TEXT)",
                "CREATE TABLE IF NOT EXISTS staff_notes(id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid TEXT NOT NULL, target_name TEXT NOT NULL, staff_uuid TEXT NOT NULL, staff_name TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS history(id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, target_uuid TEXT, target_name TEXT, staff_uuid TEXT, staff_name TEXT, type TEXT NOT NULL, detail TEXT, related_id TEXT)",
                "CREATE TABLE IF NOT EXISTS snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid TEXT NOT NULL, target_name TEXT NOT NULL, kind TEXT NOT NULL, inventory TEXT NOT NULL, armor TEXT NOT NULL, ender TEXT, level INTEGER NOT NULL, exp REAL NOT NULL, world TEXT, x REAL, y REAL, z REAL, cause TEXT, keep_inventory INTEGER NOT NULL, created_at INTEGER NOT NULL, applied INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS queued_rollbacks(id INTEGER PRIMARY KEY AUTOINCREMENT, snapshot_id INTEGER NOT NULL, target_uuid TEXT NOT NULL, queued_by_uuid TEXT NOT NULL, queued_by_name TEXT NOT NULL, created_at INTEGER NOT NULL, applied_at INTEGER, applied INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS state(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS vanished(uuid TEXT PRIMARY KEY, name TEXT NOT NULL, updated_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS frozen(uuid TEXT PRIMARY KEY, name TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, reason TEXT, updated_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS staff_backups(uuid TEXT PRIMARY KEY, name TEXT NOT NULL, inventory TEXT NOT NULL, armor TEXT NOT NULL, offhand TEXT NOT NULL, level INTEGER NOT NULL, exp REAL NOT NULL, gamemode TEXT NOT NULL, allow_flight INTEGER NOT NULL, flying INTEGER NOT NULL, health REAL NOT NULL, food INTEGER NOT NULL, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, staff_uuid TEXT, staff_name TEXT, action TEXT NOT NULL, target_uuid TEXT, target_name TEXT, reason TEXT, result TEXT, related_id TEXT)",
                "CREATE TABLE IF NOT EXISTS keyall_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, staff_uuid TEXT NOT NULL, staff_name TEXT NOT NULL, items TEXT NOT NULL, recipients TEXT NOT NULL, created_at INTEGER NOT NULL, completed INTEGER NOT NULL)"
        );
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    public record PlayerIdentity(UUID uuid, String name, String lastIp, long firstSeen, long lastSeen) {
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T value) throws Exception;
    }
}
