package io.cockroachdb.jdbc;

/**
 * Provides CockroachDB JDBC driver version metadata.
 *
 * @author Kai Niemi
 */
public abstract class CockroachDriverInfo {
    private CockroachDriverInfo() {
    }

    public static final int MAJOR_VERSION = 2;

    public static final int MINOR_VERSION = 0;

    public static final int MICRO_VERSION = 1;

    public static final String DRIVER_NAME = "CockroachDB JDBC Driver";

    public static final String DRIVER_VERSION = MAJOR_VERSION + "." + MINOR_VERSION + "." + MICRO_VERSION;

    public static final String DRIVER_FULL_NAME = DRIVER_NAME + " " + DRIVER_VERSION;

    // JDBC specification
    public static final String JDBC_VERSION = "4.2";

    public static final int JDBC_MAJOR_VERSION = JDBC_VERSION.charAt(0) - '0';

    public static final int JDBC_MINOR_VERSION = JDBC_VERSION.charAt(2) - '0';
}
