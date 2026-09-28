package me.jade.ariServerUtil.grace;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class GraceCountdown {
    private long lastTitle = -1;

    public static long secondsRemaining(Instant end, Instant now) {
        if (end == null || !end.isAfter(now)) return 0;
        return (Duration.between(now, end).toMillis() + 999) / 1000;
    }

    public static String format(long seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    public boolean shouldShowTitle(long seconds) {
        if (seconds < 1 || seconds > 5 || lastTitle == seconds) return false;
        lastTitle = seconds;
        return true;
    }

    public void reset() { lastTitle = -1; }
}
