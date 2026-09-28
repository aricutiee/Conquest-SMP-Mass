package me.jade.ariServerUtil.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir Path directory;

    @Test
    void startsFreshDatabaseAndPreservesDataAfterRestart() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("serverutil.db");
        try (Connection connection = DriverManager.getConnection(url)) {
            Database.initialize(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO state(key, value) VALUES('test', 'preserved')");
                try (ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
                try (ResultSet result = statement.executeQuery("PRAGMA journal_mode")) {
                    assertTrue(result.next());
                    assertEquals("wal", result.getString(1));
                }
            }
        }
        try (Connection connection = DriverManager.getConnection(url)) {
            Database.initialize(connection);
            try (Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery("SELECT value FROM state WHERE key='test'")) {
                    assertTrue(result.next());
                    assertEquals("preserved", result.getString(1));
                }
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
                try (ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                    assertTrue(result.next());
                    assertEquals("ok", result.getString(1));
                }
            }
        }
    }
}
