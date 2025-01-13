package io.cockroachdb.jdbc.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags(value = {
        @Tag("unit-test")
})
public class CalendarVersionTest {
    @Test
    public void whenParsingServerVersion_expectCalendarVersioning() {
        Pattern p = Pattern.compile("CockroachDB CCL v(\\d+)\\.(\\d+)\\.(\\d+)\\s.*");
        Matcher m = p.matcher("CockroachDB CCL v22.1.10 (x86_64-pc-linux-gnu, built 2022/10/27 19:46:05, go1.17.11)");
        Assertions.assertTrue(m.matches());
        Assertions.assertEquals(3, m.groupCount());
        Assertions.assertEquals("22", m.group(1));
        Assertions.assertEquals("1", m.group(2));
        Assertions.assertEquals("10", m.group(3));
    }

    @Test
    public void whenCreatingValidCalendarVersion_expectSuccess() {
        CalendarVersion cv = CalendarVersion.of(
                "CockroachDB CCL v22.1.10 (x86_64-pc-linux-gnu, built 2022/10/27 19:46:05, go1.17.11)");
        Assertions.assertEquals(22, cv.getMajor());
        Assertions.assertEquals(1, cv.getMinor());
        Assertions.assertEquals("10", cv.getPatch());
    }

    @Test
    public void whenCreatingValidCalendarVersionPreRelease_expectSuccess() {
        CalendarVersion cv = CalendarVersion.of(
                "CockroachDB CCL v22.1.0-rc.1 (x86_64-pc-linux-gnu, built 2022/10/27 19:46:05, go1.17.11)");
        Assertions.assertEquals(22, cv.getMajor());
        Assertions.assertEquals(1, cv.getMinor());
        Assertions.assertEquals("0-rc.1", cv.getPatch());
    }

    @Test
    public void whenCreatingInvalidCalendarVersion_expectFail() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            CalendarVersion.of("CockroachDB CCL 22.1.10 (x86_64-pc-linux-gnu, built 2022/10/27 19:46:05, go1.17.11)");
        });
    }
}
