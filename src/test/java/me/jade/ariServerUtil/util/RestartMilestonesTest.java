package me.jade.ariServerUtil.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestartMilestonesTest {
    @Test
    void returnsMilestonesCrossedDuringTick() {
        List<Duration> due = RestartMilestones.dueMilestones(Duration.ofSeconds(65), Duration.ofSeconds(28), List.of(Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofSeconds(10)));
        assertEquals(List.of(Duration.ofMinutes(1), Duration.ofSeconds(30)), due);
    }
}
