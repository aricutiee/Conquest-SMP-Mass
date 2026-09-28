package me.jade.ariServerUtil.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DurationParserTest {
    @Test
    void parsesSingleUnits() {
        assertEquals(Duration.ofSeconds(2), DurationParser.parse("2sec"));
        assertEquals(Duration.ofMinutes(30), DurationParser.parse("30min"));
        assertEquals(Duration.ofHours(1), DurationParser.parse("1hr"));
        assertEquals(Duration.ofDays(2), DurationParser.parse("2d"));
        assertEquals(Duration.ofDays(7), DurationParser.parse("1w"));
    }

    @Test
    void parsesCombinedFormats() {
        assertEquals(Duration.ofDays(1).plusHours(12).plusMinutes(30), DurationParser.parse("1d12h30m"));
    }

    @Test
    void rejectsInvalidDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1x"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1h bad"));
    }
}
