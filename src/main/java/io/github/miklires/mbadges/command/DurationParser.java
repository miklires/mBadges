package io.github.miklires.mbadges.command;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Duration parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("duration is empty");
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(normalized);
        long seconds = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("invalid duration: " + input);
            long amount = Long.parseLong(matcher.group(1));
            long multiplier = switch (matcher.group(2)) {
                case "s" -> 1;
                case "m" -> 60;
                case "h" -> 3_600;
                case "d" -> 86_400;
                case "w" -> 604_800;
                default -> throw new IllegalArgumentException("invalid duration unit");
            };
            seconds = Math.addExact(seconds, Math.multiplyExact(amount, multiplier));
            end = matcher.end();
        }
        if (end != normalized.length() || seconds <= 0) throw new IllegalArgumentException("invalid duration: " + input);
        return Duration.ofSeconds(seconds);
    }
}
