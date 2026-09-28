package dev.turtleroles.storage;

import dev.turtleroles.policy.Actor;
import dev.turtleroles.role.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class PlayerRepository {
    private final SQLiteDatabase database;

    public PlayerRepository(SQLiteDatabase database) {
        this.database = database;
    }

    public synchronized PlayerRecord upsertKnownPlayer(UUID uuid, String name) throws SQLException {
        long now = Instant.now().toEpochMilli();
        String lowerName = name.toLowerCase(Locale.ROOT);
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            // A login mode change can give the same name a new UUID. Keep the old
            // record and its permissions attached to its UUID, but release the name.
            try (PreparedStatement releaseName = connection.prepareStatement("""
                UPDATE players SET lower_name = '~' || uuid WHERE lower_name = ? AND uuid <> ?
                """)) {
                releaseName.setString(1, lowerName);
                releaseName.setString(2, uuid.toString());
                releaseName.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO players(uuid, last_name, lower_name, role_id, revision, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                last_name = excluded.last_name,
                lower_name = excluded.lower_name,
                updated_at = excluded.updated_at
            """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setString(3, lowerName);
                statement.setString(4, Role.MEMBER.id());
                statement.setLong(5, now);
                statement.setLong(6, now);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
        return findByUuid(uuid).orElseThrow();
    }

    public synchronized Optional<PlayerRecord> findByUuid(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<PlayerRecord> findByName(String name) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT * FROM players WHERE lower_name = ?")) {
            statement.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<PlayerRecord> resolveKnown(String input) throws SQLException {
        try {
            return findByUuid(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            return findByName(input);
        }
    }

    public synchronized List<PlayerRecord> listPlayers() throws SQLException {
        List<PlayerRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection().prepareStatement("SELECT * FROM players ORDER BY role_id, lower_name")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    records.add(map(rs));
                }
            }
        }
        return records;
    }

    public synchronized void setRole(UUID playerUuid, Role newRole, Actor actor, String reason) throws SQLException {
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            setRoleInOpenTransaction(playerUuid, newRole, actor, reason);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public synchronized long ownerCount() throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT COUNT(*) FROM players WHERE role_id = ?")) {
            statement.setString(1, Role.OWNER.id());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public synchronized Optional<PlayerRecord> currentOwner() throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT * FROM players WHERE role_id = ? LIMIT 1")) {
            statement.setString(1, Role.OWNER.id());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public synchronized int ownershipRevision() throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT COALESCE(MAX(revision), 0) FROM ownership_audit")) {
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public synchronized void bootstrapOwner(PlayerRecord target, Actor actor) throws SQLException {
        if (ownerCount() > 0) {
            throw new SQLException("Owner already exists.");
        }
        setRole(target.uuid(), Role.OWNER, actor, "Owner bootstrap");
        writeOwnershipAudit(null, target.uuid(), actor, ownershipRevision() + 1);
    }

    public synchronized void transferOwner(PlayerRecord oldOwner, PlayerRecord newOwner, Actor actor, int expectedRevision) throws SQLException {
        int actualRevision = ownershipRevision();
        if (actualRevision != expectedRevision) {
            throw new SQLException("Ownership confirmation expired.");
        }
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            setRoleInOpenTransaction(oldOwner.uuid(), Role.CO_OWNER, actor, "Ownership transfer - previous owner");
            setRoleInOpenTransaction(newOwner.uuid(), Role.OWNER, actor, "Ownership transfer - new owner");
            writeOwnershipAudit(oldOwner.uuid(), newOwner.uuid(), actor, expectedRevision + 1);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    private void setRoleInOpenTransaction(UUID playerUuid, Role newRole, Actor actor, String reason) throws SQLException {
        PlayerRecord current = findByUuid(playerUuid).orElseThrow(() -> new SQLException("Unknown player: " + playerUuid));
        long now = Instant.now().toEpochMilli();
        try (PreparedStatement update = connection().prepareStatement("""
            UPDATE players SET role_id = ?, revision = revision + 1, updated_at = ? WHERE uuid = ?
            """)) {
            update.setString(1, newRole.id());
            update.setLong(2, now);
            update.setString(3, playerUuid.toString());
            update.executeUpdate();
        }
        try (PreparedStatement history = connection().prepareStatement("""
            INSERT INTO role_history(player_uuid, old_role_id, new_role_id, actor_uuid, actor_name, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)) {
            history.setString(1, playerUuid.toString());
            history.setString(2, current.role().id());
            history.setString(3, newRole.id());
            history.setString(4, actor.uuid() == null ? null : actor.uuid().toString());
            history.setString(5, actor.name());
            history.setString(6, reason);
            history.setLong(7, now);
            history.executeUpdate();
        }
    }

    private void writeOwnershipAudit(UUID oldOwner, UUID newOwner, Actor actor, int revision) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("""
            INSERT INTO ownership_audit(old_owner_uuid, new_owner_uuid, actor_uuid, actor_name, revision, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, oldOwner == null ? null : oldOwner.toString());
            statement.setString(2, newOwner.toString());
            statement.setString(3, actor.uuid() == null ? null : actor.uuid().toString());
            statement.setString(4, actor.name());
            statement.setInt(5, revision);
            statement.setLong(6, Instant.now().toEpochMilli());
            statement.executeUpdate();
        }
    }

    private PlayerRecord map(ResultSet rs) throws SQLException {
        return new PlayerRecord(
            UUID.fromString(rs.getString("uuid")),
            rs.getString("last_name"),
            rs.getString("lower_name"),
            Role.byId(rs.getString("role_id")).orElse(Role.MEMBER),
            rs.getInt("revision"),
            Instant.ofEpochMilli(rs.getLong("updated_at"))
        );
    }

    private Connection connection() {
        return database.connection();
    }
}
