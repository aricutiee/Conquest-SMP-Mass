package dev.turtleroles.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SQLiteDatabase implements AutoCloseable {
    private final Path databasePath;
    private Connection connection;

    public SQLiteDatabase(Path databasePath) {
        this.databasePath = databasePath;
    }

    public synchronized void open() throws SQLException, IOException {
        Files.createDirectories(databasePath.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        migrate();
    }

    public synchronized Connection connection() {
        if (connection == null) {
            throw new IllegalStateException("Database is not open.");
        }
        return connection;
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL PRIMARY KEY
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT NOT NULL PRIMARY KEY,
                    last_name TEXT NOT NULL,
                    lower_name TEXT NOT NULL UNIQUE,
                    role_id TEXT NOT NULL,
                    revision INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS role_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    old_role_id TEXT,
                    new_role_id TEXT NOT NULL,
                    actor_uuid TEXT,
                    actor_name TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(player_uuid) REFERENCES players(uuid)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    target_name TEXT NOT NULL,
                    issuer_uuid TEXT,
                    issuer_name TEXT NOT NULL,
                    issuer_role_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER,
                    revoked INTEGER NOT NULL DEFAULT 0,
                    revoked_by_uuid TEXT,
                    revoked_by_name TEXT,
                    revoked_reason TEXT,
                    revoked_at INTEGER
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_target_type_active ON punishments(target_uuid, type, revoked, expires_at)");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ownership_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    old_owner_uuid TEXT,
                    new_owner_uuid TEXT NOT NULL,
                    actor_uuid TEXT,
                    actor_name TEXT NOT NULL,
                    revision INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            statement.execute("INSERT OR IGNORE INTO schema_version(version) VALUES (1)");
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }
}
