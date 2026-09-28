package me.jade.ariServerUtil.util;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SlowModeTrackerTest {
    @Test
    void blocksUntilDelayExpires() {
        MutableClock clock = new MutableClock();
        SlowModeTracker tracker = new SlowModeTracker(clock);
        UUID player = UUID.randomUUID();
        assertTrue(tracker.tryMark(player, Duration.ofSeconds(10)));
        assertFalse(tracker.tryMark(player, Duration.ofSeconds(10)));
        clock.advance(Duration.ofSeconds(10));
        assertTrue(tracker.tryMark(player, Duration.ofSeconds(10)));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
