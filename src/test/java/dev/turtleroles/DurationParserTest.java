package dev.turtleroles;

import dev.turtleroles.util.DurationParser;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {
    @Test
    void parsesCompoundDurations() {
        assertEquals(Duration.ofMinutes(30), DurationParser.parseStrict("30m"));
        assertEquals(Duration.ofHours(2), DurationParser.parseStrict("2h"));
        assertEquals(Duration.ofSeconds(129_600), DurationParser.parseStrict("1d12h"));
        assertEquals(Duration.ofSeconds(604_800), DurationParser.parseStrict("7d"));
    }

    @Test
    void rejectsInvalidAndOverflowingDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseStrict("0s"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseStrict("-1h"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseStrict("7days"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseStrict("1hxyz"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseStrict("999999999999999999999999999999d"));
    }
}
