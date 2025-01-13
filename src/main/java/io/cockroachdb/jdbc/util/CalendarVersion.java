package io.cockroachdb.jdbc.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing CockroachDB version strings.
 *
 * @author Kai Niemi
 */
public final class CalendarVersion {
    private static final Pattern CALENDAR_VERSIONING = Pattern.compile(
            "CockroachDB CCL v(\\d+)\\.(\\d+)\\.(\\d+(.+)?)\\s\\(.*");

    public static CalendarVersion of(String version) {
        Matcher m = CALENDAR_VERSIONING.matcher(version);
        if (m.matches()) {
            return new CalendarVersion(m);
        }
        throw new IllegalArgumentException("Unexpected version format: " + version);
    }

    private final int major;

    private final int minor;

    private final String patch;

    private CalendarVersion(Matcher m) {
        this.major = Integer.parseInt(m.group(1));
        this.minor = Integer.parseInt(m.group(2));
        this.patch = m.group(3);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public String getPatch() {
        return patch;
    }
}
