package me.jade.ariServerUtil.grace;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class GraceCountdownTest {
    @Test void countsFiveToOneOnceEachAndDoesNotShowZeroEarly() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plusSeconds(8);
        GraceCountdown countdown = new GraceCountdown();
        var titles = new ArrayList<Long>();
        for (long millis = 0; millis <= 9000; millis += 250) {
            long seconds = GraceCountdown.secondsRemaining(end, start.plusMillis(millis));
            if (countdown.shouldShowTitle(seconds)) titles.add(seconds);
        }
        assertEquals(java.util.List.of(5L, 4L, 3L, 2L, 1L), titles);
        assertEquals(1, GraceCountdown.secondsRemaining(end, end.minusMillis(1)));
        assertEquals(0, GraceCountdown.secondsRemaining(end, end));
        countdown.reset();
        assertTrue(countdown.shouldShowTitle(5));
    }

    @Test void formatsLongPeriodsAndExpiredTimers() {
        assertEquals("01:05", GraceCountdown.format(65));
        assertEquals("90:00", GraceCountdown.format(5400));
        assertEquals(0, GraceCountdown.secondsRemaining(null, Instant.now()));
        assertEquals(0, GraceCountdown.secondsRemaining(Instant.EPOCH, Instant.now()));
    }
}
