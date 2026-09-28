package dev.turtleroles.punishment;

import dev.turtleroles.role.Role;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PunishmentCase(
    long id,
    PunishmentType type,
    UUID targetUuid,
    String targetName,
    UUID issuerUuid,
    String issuerName,
    Role issuerRoleSnapshot,
    String reason,
    Instant createdAt,
    Instant expiresAt,
    boolean revoked,
    UUID revokedByUuid,
    String revokedByName,
    String revokedReason,
    Instant revokedAt
) {
    public boolean activeAt(Instant now) {
        return !revoked && (expiresAt == null || expiresAt.isAfter(now));
    }

    public Optional<Instant> optionalExpiry() {
        return Optional.ofNullable(expiresAt);
    }
}
