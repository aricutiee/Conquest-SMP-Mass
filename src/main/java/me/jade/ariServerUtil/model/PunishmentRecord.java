package me.jade.ariServerUtil.model;

import java.time.Instant;
import java.util.UUID;

public record PunishmentRecord(
        long id,
        UUID targetUuid,
        String targetName,
        UUID staffUuid,
        String staffName,
        PunishmentType type,
        String reason,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        Long relatedReportId
) {
    public boolean isExpired(Instant now) {
        return active && expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isBan() {
        return type == PunishmentType.BAN || type == PunishmentType.TEMP_BAN;
    }

    public boolean isMute() {
        return type == PunishmentType.MUTE || type == PunishmentType.TEMP_MUTE;
    }
}
