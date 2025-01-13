package io.cockroachdb.jdbc.integrationtest.support;

public abstract class StringUtils {
    private StringUtils() {
    }

    public static boolean hasLength(String s) {
        return s != null && !s.isEmpty();
    }
}
