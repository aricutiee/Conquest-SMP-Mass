package me.jade.ariServerUtil.model;

import java.time.Instant;
import java.util.UUID;

public record ReportRecord(
        long id,
        UUID reporterUuid,
        String reporterName,
        UUID reportedUuid,
        String reportedName,
        String reason,
        Instant createdAt,
        ReportStatus status,
        UUID assignedStaff,
        String staffNotes,
        Instant resolutionTime,
        String resolutionResult
) {
}
