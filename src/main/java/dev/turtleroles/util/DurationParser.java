package dev.turtleroles.util;

import java.math.BigInteger;
import java.time.Duration;

public final class DurationParser {
    private DurationParser() {
    }

    public static Duration parseStrict(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Duration is required.");
        }
        String text = raw.trim().toLowerCase();
        BigInteger totalSeconds = BigInteger.ZERO;
        int index = 0;
        boolean sawPart = false;
        while (index < text.length()) {
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected a number in duration.");
            }
            if (index >= text.length()) {
                throw new IllegalArgumentException("Missing duration suffix.");
            }
            BigInteger amount = new BigInteger(text.substring(start, index));
            char unit = text.charAt(index++);
            long multiplier = switch (unit) {
                case 's' -> 1L;
                case 'm' -> 60L;
                case 'h' -> 3_600L;
                case 'd' -> 86_400L;
                default -> throw new IllegalArgumentException("Unknown duration suffix: " + unit);
            };
            totalSeconds = totalSeconds.add(amount.multiply(BigInteger.valueOf(multiplier)));
            if (totalSeconds.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException("Duration is too large.");
            }
            sawPart = true;
        }
        if (!sawPart || totalSeconds.signum() <= 0) {
            throw new IllegalArgumentException("Duration must be positive.");
        }
        return Duration.ofSeconds(totalSeconds.longValueExact());
    }
}
