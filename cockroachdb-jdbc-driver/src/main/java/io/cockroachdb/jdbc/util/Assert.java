package io.cockroachdb.jdbc.util;

/**
 * Basic assertion methods.
 *
 * @author Kai Niemi
 */
public abstract class Assert {
    private Assert() {
    }

    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void hasText(String text, String message) {
        if (text == null || text.length() == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
