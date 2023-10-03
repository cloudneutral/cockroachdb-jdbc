package io.cockroachdb.jdbc.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for formatting and parsing Duration's.
 */
public abstract class DurationFormat {
    private static final Pattern DURATION_PATTERN = Pattern.compile("([0-9]+)\\s*([smhdw]+)", Pattern.CASE_INSENSITIVE);

    private DurationFormat() {
    }

    /**
     * Parse a duration expression with instant tokens, or when absent, milliseconds (epoch).
     *
     * @param durationOrMillis time duration expression
     * @return a duration
     */
    public static Duration parseDuration(String durationOrMillis) {
        Matcher matcher = DURATION_PATTERN.matcher(durationOrMillis.toLowerCase(Locale.ENGLISH));
        Duration instant = Duration.ZERO;
        if (matcher.find()) {
            do {
                int ordinal = Integer.parseInt(matcher.group(1));
                String token = matcher.group(2);
                switch (token) {
                    case "ms":
                        instant = instant.plus(Duration.ofMillis(ordinal));
                        break;
                    case "s":
                        instant = instant.plus(Duration.ofSeconds(ordinal));
                        break;
                    case "m":
                        instant = instant.plus(Duration.ofMinutes(ordinal));
                        break;
                    case "h":
                        instant = instant.plus(Duration.ofHours(ordinal));
                        break;
                    case "d":
                        instant = instant.plus(Duration.ofDays(ordinal));
                        break;
                    case "w":
                        instant = instant.plus(Duration.ofDays(ordinal * 7L));
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid token: " + token);
                }
            } while (matcher.find());
            return instant;
        } else {
            return Duration.ofMillis(Long.parseLong(durationOrMillis));
        }
    }
}
