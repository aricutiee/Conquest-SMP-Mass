package me.jade.ariServerUtil.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PunishmentRecordTest {
    @Test
    void detectsExpirationOnlyForActiveTemporaryRecords() {
        Instant now = Instant.now();
        PunishmentRecord expired = new PunishmentRecord(1, UUID.randomUUID(), "Target", UUID.randomUUID(), "Staff", PunishmentType.TEMP_BAN, "Reason", now.minusSeconds(60), now.minusSeconds(1), true, null);
        PunishmentRecord permanent = new PunishmentRecord(2, UUID.randomUUID(), "Target", UUID.randomUUID(), "Staff", PunishmentType.BAN, "Reason", now, null, true, null);
        PunishmentRecord inactive = new PunishmentRecord(3, UUID.randomUUID(), "Target", UUID.randomUUID(), "Staff", PunishmentType.TEMP_MUTE, "Reason", now.minusSeconds(60), now.minusSeconds(1), false, null);
        assertTrue(expired.isExpired(now));
        assertFalse(permanent.isExpired(now));
        assertFalse(inactive.isExpired(now));
    }
}
