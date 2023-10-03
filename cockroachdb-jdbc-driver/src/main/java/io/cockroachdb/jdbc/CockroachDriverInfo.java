package io.cockroachdb.jdbc;

/**
 * Provides CockroachDB JDBC driver version metadata.
 */
public abstract class CockroachDriverInfo {
    private CockroachDriverInfo() {
    }

    // Driver name
    public static final String DRIVER_NAME = "CockroachDB JDBC Driver";

    public static final String DRIVER_VERSION = "1.0.0";

    public static final String DRIVER_FULL_NAME = DRIVER_NAME + " " + DRIVER_VERSION;

    // Driver version
    public static final int MAJOR_VERSION = 0;

    public static final int MINOR_VERSION = 9;

    // JDBC specification
    public static final String JDBC_VERSION = "4.2";

    public static final int JDBC_MAJOR_VERSION = JDBC_VERSION.charAt(0) - '0';

    public static final int JDBC_MINOR_VERSION = JDBC_VERSION.charAt(2) - '0';
}
