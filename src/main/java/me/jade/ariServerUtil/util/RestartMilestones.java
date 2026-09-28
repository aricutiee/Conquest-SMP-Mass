package me.jade.ariServerUtil.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RestartMilestones {
    private RestartMilestones() {
    }

    public static List<Duration> dueMilestones(Duration previousRemaining, Duration currentRemaining, List<Duration> milestones) {
        List<Duration> due = new ArrayList<>();
        for (Duration milestone : milestones) {
            if (previousRemaining.compareTo(milestone) > 0 && currentRemaining.compareTo(milestone) <= 0 && !currentRemaining.isNegative()) {
                due.add(milestone);
            }
        }
        due.sort(Comparator.reverseOrder());
        return due;
    }
}
