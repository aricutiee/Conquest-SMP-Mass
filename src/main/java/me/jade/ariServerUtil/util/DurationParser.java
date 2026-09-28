package me.jade.ariServerUtil.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+)(sec|s|second|seconds|min|m|minute|minutes|hr|h|hour|hours|d|day|days|w|week|weeks)", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration is required.");
        }
        String normalized = input.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = PART.matcher(normalized);
        long seconds = 0;
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() != index) {
                throw new IllegalArgumentException("Invalid duration near '" + normalized.substring(index) + "'.");
            }
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            seconds = Math.addExact(seconds, Math.multiplyExact(amount, multiplier(unit)));
            index = matcher.end();
        }
        if (index != normalized.length() || seconds <= 0) {
            throw new IllegalArgumentException("Use durations like 30min, 1hr, 2d, or 1d12h30m.");
        }
        return Duration.ofSeconds(seconds);
    }

    public static String human(Duration duration) {
        long seconds = duration.getSeconds();
        long weeks = seconds / 604800;
        seconds %= 604800;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder builder = new StringBuilder();
        append(builder, weeks, "week");
        append(builder, days, "day");
        append(builder, hours, "hour");
        append(builder, minutes, "minute");
        append(builder, seconds, "second");
        return builder.length() == 0 ? "0 seconds" : builder.toString().trim();
    }

    private static void append(StringBuilder builder, long amount, String unit) {
        if (amount <= 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(amount).append(' ').append(unit);
        if (amount != 1) {
            builder.append('s');
        }
    }

    private static long multiplier(String unit) {
        return switch (unit.charAt(0)) {
            case 's' -> 1;
            case 'm' -> 60;
            case 'h' -> 3600;
            case 'd' -> 86400;
            case 'w' -> 604800;
            default -> throw new IllegalArgumentException("Unknown duration unit.");
        };
    }
}
