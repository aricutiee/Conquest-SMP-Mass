package dev.turtleroles.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PvpHourClockTest {
    private static final long HOUR = 3_600_000;
    @Test void firstStartIsFourHoursAwayAndInitiallyOff() {
        var clock = new PvpHourClock(100, 0, 0, HOUR, HOUR*4);
        assertFalse(clock.active()); assertEquals(100+HOUR*4, clock.nextStart);
        assertFalse(clock.due(99+HOUR*4)); assertTrue(clock.due(100+HOUR*4));
    }
    @Test void oneHourDurationAndFourHoursBetweenStarts() {
        var clock = new PvpHourClock(100, 0, 0, HOUR, HOUR*4);
        assertTrue(clock.start(200)); assertFalse(clock.start(300));
        assertEquals(200+HOUR, clock.endsAt); assertEquals(200+HOUR*4, clock.nextStart);
        assertFalse(clock.expired(199+HOUR)); assertTrue(clock.expired(200+HOUR));
        assertTrue(clock.stop()); assertFalse(clock.stop()); assertFalse(clock.due(200+HOUR));
        assertTrue(clock.due(200+HOUR*4));
    }
    @Test void restartResumesOnlyRemainingTime() {
        var clock = new PvpHourClock(500, 1000, 2000, HOUR, HOUR*4);
        assertTrue(clock.active()); assertEquals(1000, clock.endsAt); assertEquals(2000, clock.nextStart);
    }
    @Test void expiredWindowIsOffAfterRestartAndMissedStartsDoNotBurst() {
        var clock = new PvpHourClock(5000, 1000, 2000, HOUR, HOUR*4);
        assertFalse(clock.active()); assertFalse(clock.due(5000)); assertEquals(5000+HOUR*4, clock.nextStart);
    }
    @Test void manualStopKeepsNextScheduledStart() {
        var clock = new PvpHourClock(100, 0, 0, HOUR, HOUR*4);
        clock.start(200); clock.stop(); assertEquals(200+HOUR*4, clock.nextStart);
    }
}
