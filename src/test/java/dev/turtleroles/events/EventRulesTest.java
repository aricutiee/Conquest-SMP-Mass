package dev.turtleroles.events;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EventRulesTest {
    @Test void durationAcceptsSpokenAndCompactUnits() {
        assertEquals(Duration.ofSeconds(30), EventRules.duration("30s"));
        assertEquals(Duration.ofSeconds(30), EventRules.duration("30 seconds"));
        assertEquals(Duration.ofMinutes(5), EventRules.duration("5 minutes"));
        assertEquals(Duration.ofHours(30), EventRules.duration("30h"));
        assertEquals(Duration.ofMinutes(90), EventRules.duration("1h 30m"));
        assertThrows(IllegalArgumentException.class, () -> EventRules.duration("5m hello"));
        assertThrows(IllegalArgumentException.class, () -> EventRules.duration("0s"));
    }

    @Test void everyAssassinHasOneTargetAndOneHunter() {
        List<String> players = List.of("A", "B", "C", "D", "E");
        Map<String, String> cycle = EventRules.cycle(players, new Random(7));
        assertEquals(new HashSet<>(players), cycle.keySet());
        assertEquals(new HashSet<>(players), new HashSet<>(cycle.values()));
        cycle.forEach((hunter, target) -> assertNotEquals(hunter, target));
        assertEquals(players.size(), new HashSet<>(cycle.values()).size());
        assertThrows(IllegalArgumentException.class, () -> EventRules.cycle(List.of("A", "B"), new Random(1)));
    }

    @Test void eliminationRepairsCycleAndLeavesFinalDuel() {
        Map<String, String> cycle = Map.of("A", "B", "B", "C", "C", "D", "D", "A");
        Map<String, String> after = EventRules.removeFromCycle(cycle, "B");
        assertEquals(Map.of("A", "C", "C", "D", "D", "A"), after);
        Map<String, String> duel = EventRules.removeFromCycle(after, "C");
        assertEquals(Map.of("A", "D", "D", "A"), duel);
    }

    @Test void bladeUsesOneOneThenOnePointTwoOneAndResets() {
        assertEquals(1.0, EventRules.bladeMultiplier(1, 1.1), 0.00001);
        assertEquals(1.1, EventRules.bladeMultiplier(2, 1.1), 0.00001);
        assertEquals(1.21, EventRules.bladeMultiplier(3, 1.1), 0.00001);
    }

    @Test void rareAllocationUsesConfiguredOneInEight() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000005");
        long rare = java.util.stream.IntStream.range(0, 32).filter(i -> EventRules.rare(i, 8, id)).count();
        assertEquals(4, rare);
    }
}
