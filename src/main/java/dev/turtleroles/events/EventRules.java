package dev.turtleroles.events;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, deterministic rules shared by the live event handlers and tests. */
public final class EventRules {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)", Pattern.CASE_INSENSITIVE);

    private EventRules() {}

    public static Duration duration(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Enter a duration such as 30s, 5 minutes, or 1h 30m.");
        Matcher matcher = PART.matcher(input.trim());
        int end = 0;
        long seconds = 0;
        while (matcher.find()) {
            if (!input.substring(end, matcher.start()).isBlank()) throw new IllegalArgumentException("Use a duration such as 30s or 1h 30m.");
            long amount;
            try { amount = Long.parseLong(matcher.group(1)); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Duration is too large."); }
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            long multiplier = unit == 's' ? 1 : unit == 'm' ? 60 : 3600;
            try { seconds = Math.addExact(seconds, Math.multiplyExact(amount, multiplier)); }
            catch (ArithmeticException ex) { throw new IllegalArgumentException("Duration is too large."); }
            end = matcher.end();
        }
        if (!input.substring(end).isBlank() || seconds <= 0) throw new IllegalArgumentException("Use a positive duration such as 30s or 1h 30m.");
        return Duration.ofSeconds(seconds);
    }

    public static <T> Map<T, T> cycle(List<T> participants, java.util.Random random) {
        if (participants.size() < 3) throw new IllegalArgumentException("At least three participants are required.");
        List<T> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled, random);
        Map<T, T> result = new LinkedHashMap<>();
        for (int i = 0; i < shuffled.size(); i++) result.put(shuffled.get(i), shuffled.get((i + 1) % shuffled.size()));
        return result;
    }

    public static <T> Map<T, T> removeFromCycle(Map<T, T> targets, T removed) {
        Map<T, T> result = new LinkedHashMap<>(targets);
        T successor = result.remove(removed);
        if (successor == null) return result;
        if (result.size() == 1) {
            result.replaceAll((key, value) -> key);
        } else {
            result.replaceAll((key, value) -> value.equals(removed) ? successor : value);
        }
        return result;
    }

    public static double bladeMultiplier(int hit, double step) {
        return Math.pow(step, Math.max(0, Math.min(2, hit - 1)));
    }

    public static boolean rare(int index, int oneIn, UUID eventId) {
        return Math.floorMod(index + eventId.hashCode(), Math.max(1, oneIn)) == 0;
    }
}
