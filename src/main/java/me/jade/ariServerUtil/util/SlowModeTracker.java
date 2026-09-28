package me.jade.ariServerUtil.util;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SlowModeTracker {
    private final Clock clock;
    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();

    public SlowModeTracker(Clock clock) {
        this.clock = clock;
    }

    public Duration remaining(UUID player, Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return Duration.ZERO;
        }
        long now = clock.millis();
        Long last = lastMessage.get(player);
        if (last == null) {
            return Duration.ZERO;
        }
        long remainingMillis = delay.toMillis() - (now - last);
        return remainingMillis <= 0 ? Duration.ZERO : Duration.ofMillis(remainingMillis);
    }

    public boolean tryMark(UUID player, Duration delay) {
        Duration remaining = remaining(player, delay);
        if (!remaining.isZero() && !remaining.isNegative()) {
            return false;
        }
        lastMessage.put(player, clock.millis());
        return true;
    }

    public void clear(UUID player) {
        lastMessage.remove(player);
    }
}
