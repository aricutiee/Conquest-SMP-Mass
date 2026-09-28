package dev.turtleroles.storage;

import dev.turtleroles.policy.Actor;
import dev.turtleroles.punishment.PunishmentCase;
import dev.turtleroles.punishment.PunishmentType;
import dev.turtleroles.role.Role;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PunishmentRepository {
    private final SQLiteDatabase database;

    public PunishmentRepository(SQLiteDatabase database) {
        this.database = database;
    }

    public synchronized PunishmentCase create(PunishmentType type, PlayerRecord target, Actor issuer, String reason, Instant expiresAt) throws SQLException {
        long now = Instant.now().toEpochMilli();
        try (PreparedStatement statement = database.connection().prepareStatement("""
            INSERT INTO punishments(type, target_uuid, target_name, issuer_uuid, issuer_name, issuer_role_id, reason, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, type.name());
            statement.setString(2, target.uuid().toString());
            statement.setString(3, target.lastName());
            statement.setString(4, issuer.uuid() == null ? null : issuer.uuid().toString());
            statement.setString(5, issuer.name());
            statement.setString(6, issuer.role().id());
            statement.setString(7, reason);
            statement.setLong(8, now);
            if (expiresAt == null) {
                statement.setObject(9, null);
            } else {
                statement.setLong(9, expiresAt.toEpochMilli());
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated case ID returned.");
                }
                return get(keys.getLong(1)).orElseThrow();
            }
        }
    }

    public synchronized Optional<PunishmentCase> get(long id) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("SELECT * FROM punishments WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public synchronized List<PunishmentCase> listFor(UUID targetUuid, int limit, int offset) throws SQLException {
        List<PunishmentCase> cases = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT * FROM punishments WHERE target_uuid = ? ORDER BY id DESC LIMIT ? OFFSET ?
            """)) {
            statement.setString(1, targetUuid.toString());
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    cases.add(map(rs));
                }
            }
        }
        return cases;
    }

    public synchronized Optional<PunishmentCase> activeMute(UUID targetUuid, Instant now) throws SQLException {
        return activeOfTypes(targetUuid, now, PunishmentType.TEMP_MUTE, PunishmentType.PERM_MUTE);
    }

    public synchronized Optional<PunishmentCase> activeBan(UUID targetUuid, Instant now) throws SQLException {
        return activeOfTypes(targetUuid, now, PunishmentType.TEMP_BAN, PunishmentType.PERM_BAN);
    }

    public synchronized void revoke(long id, Actor actor, String reason) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            UPDATE punishments
            SET revoked = 1, revoked_by_uuid = ?, revoked_by_name = ?, revoked_reason = ?, revoked_at = ?
            WHERE id = ? AND revoked = 0
            """)) {
            statement.setString(1, actor.uuid() == null ? null : actor.uuid().toString());
            statement.setString(2, actor.name());
            statement.setString(3, reason);
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.setLong(5, id);
            statement.executeUpdate();
        }
    }

    private Optional<PunishmentCase> activeOfTypes(UUID targetUuid, Instant now, PunishmentType temporary, PunishmentType permanent) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("""
            SELECT * FROM punishments
            WHERE target_uuid = ?
              AND type IN (?, ?)
              AND revoked = 0
              AND (expires_at IS NULL OR expires_at > ?)
            ORDER BY id DESC
            LIMIT 1
            """)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, temporary.name());
            statement.setString(3, permanent.name());
            statement.setLong(4, now.toEpochMilli());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private PunishmentCase map(ResultSet rs) throws SQLException {
        String issuerUuid = rs.getString("issuer_uuid");
        String revokedUuid = rs.getString("revoked_by_uuid");
        long expiresAt = rs.getLong("expires_at");
        boolean expiresNull = rs.wasNull();
        long revokedAt = rs.getLong("revoked_at");
        boolean revokedAtNull = rs.wasNull();
        return new PunishmentCase(
            rs.getLong("id"),
            PunishmentType.valueOf(rs.getString("type")),
            UUID.fromString(rs.getString("target_uuid")),
            rs.getString("target_name"),
            issuerUuid == null ? null : UUID.fromString(issuerUuid),
            rs.getString("issuer_name"),
            Role.byId(rs.getString("issuer_role_id")).orElse(Role.MEMBER),
            rs.getString("reason"),
            Instant.ofEpochMilli(rs.getLong("created_at")),
            expiresNull ? null : Instant.ofEpochMilli(expiresAt),
            rs.getInt("revoked") != 0,
            revokedUuid == null ? null : UUID.fromString(revokedUuid),
            rs.getString("revoked_by_name"),
            rs.getString("revoked_reason"),
            revokedAtNull ? null : Instant.ofEpochMilli(revokedAt)
        );
    }
}
