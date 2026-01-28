package com.autostartstop.util;

import com.autostartstop.Log;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-readable duration strings into Duration objects.
 * Supports single unit format: 10t (ticks), 500ms, 5s, 2m, 1h
 * If no unit is specified, the value is interpreted as milliseconds (e.g., "500" = 500ms)
 * Note: 1 tick = 50ms (20 ticks per second)
 */
public class DurationUtil {
    private static final Logger logger = Log.get(DurationUtil.class);
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(t|ms|s|m|h)?$", Pattern.CASE_INSENSITIVE);
    
    /** Milliseconds per tick (50ms = 20 ticks per second) */
    public static final long MS_PER_TICK = 50L;

    /**
     * Parses a duration string into a Duration object.
     *
     * @param input The duration string (e.g., "10t", "500ms", "5s", "2m", "1h", or "500" for milliseconds)
     * @return The parsed Duration
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Duration parse(String input) {
        logger.debug("DurationParser: parsing '{}'", input);

        if (input == null || input.isBlank()) {
            logger.error("DurationParser: input is null or blank");
            throw new IllegalArgumentException("Duration string cannot be null or empty");
        }

        String trimmed = input.trim();
        Matcher matcher = DURATION_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            logger.error("DurationParser: invalid format '{}' - expected format: <number> or <number><unit> (t, ms, s, m, h)", input);
            throw new IllegalArgumentException("Invalid duration format: " + input +
                    ". Expected format: <number> (milliseconds) or <number><unit> where unit is t (ticks), ms, s, m, or h");
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        
        // If no unit is specified, default to milliseconds
        if (unit == null || unit.isEmpty()) {
            unit = "ms";
        } else {
            unit = unit.toLowerCase();
        }

        Duration result = switch (unit) {
            case "t" -> Duration.ofMillis(value * MS_PER_TICK);
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };

        logger.debug("DurationParser: '{}' -> {}ms", input, result.toMillis());
        return result;
    }
    
    /**
     * Parses a duration string and returns the value in ticks.
     *
     * @param input The duration string (e.g., "10t", "500ms", "5s", or "500" for milliseconds)
     * @return The duration in ticks (1 tick = 50ms)
     * @throws IllegalArgumentException if the format is invalid
     */
    public static long parseToTicks(String input) {
        Duration duration = parse(input);
        return duration.toMillis() / MS_PER_TICK;
    }
    
    /**
     * Parses a duration string with a default value if parsing fails.
     *
     * @param input The duration string
     * @param defaultValue The default Duration to return if parsing fails
     * @return The parsed Duration, or defaultValue if parsing fails
     */
    public static Duration parseOrDefault(String input, Duration defaultValue) {
        if (input == null || input.isBlank()) {
            return defaultValue;
        }
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            logger.warn("DurationParser: failed to parse '{}', using default: {}", input, defaultValue);
            return defaultValue;
        }
    }

    public static String format(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }
}
