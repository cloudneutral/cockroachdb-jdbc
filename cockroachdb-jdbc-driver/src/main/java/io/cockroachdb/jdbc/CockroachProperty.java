package io.cockroachdb.jdbc;

import java.sql.DriverPropertyInfo;
import java.util.Properties;

/**
 * Enum of CockroachDB JDBC driver properties.
 */
public enum CockroachProperty {
    RETRY_TRANSIENT_ERRORS(
            "retryTransientErrors",
            Boolean.FALSE.toString(),
            false,
            "The CockroachDB JDBC driver will automatically retry transient errors (40001 state) at read, write or commit time. "
                    + "This is done by keeping track of all statements and the results during an explicit transaction, "
                    + "and if the transaction is aborted due to a transient error, it will retry the statements on a "
                    + "new connection and compare the results with the initial attempt. If the results differs, the retry attempt "
                    + "will be cancelled and result in an concurrent modification error. "
                    + "Disable this option if you want to handle aborted transactions in your own application.",
            new String[] {"true", "false"}),

    RETRY_CONNECTION_ERRORS(
            "retryConnectionErrors",
            Boolean.FALSE.toString(),
            false,
            "The CockroachDB JDBC driver will automatically retry transient connection errors "
                    + "(08001, 08003, 08004, 08006, 08007, 08S01 or 57P01 state) at read, write or commit time. "
                    + "Disable this option if you want to handle connection errors in your own application or connection pool. "
                    + "CAUTION! Retrying on non-serializable conflict errors (i.e anything but 40001) may produce duplicate outcomes "
                    + "if the SQL statements are non-idempotent.",
            new String[] {"true", "false"}),

    RETRY_LISTENER_CLASSNAME(
            "retryListenerClassName",
            "io.cockroachdb.jdbc.retry.LoggingRetryListener",
            false,
            "Name of class that implements 'io.cockroachdb.jdbc.retry.RetryListener' to be used to receive "
                    + "callback events when retries occur. "
                    + "One instance is created for each JDBC connection.",
            new String[] {}),

    RETRY_STRATEGY_CLASSNAME(
            "retryStrategyClassName",
            "io.cockroachdb.jdbc.retry.ExponentialBackoffRetryStrategy",
            false,
            "Name of class that implements 'io.cockroachdb.jdbc.retry.RetryStrategy' to be used when "
                    + "retryTransientErrors property is 'true'. "
                    + "One instance is created for each JDBC connection.",
            new String[] {}),

    RETRY_MAX_ATTEMPTS(
            "retryMaxAttempts",
            "15",
            false,
            "Maximum number of retry attempts on transient failures (connection errors / serialization conflicts). "
                    + "If this limit is exceeded, the driver will throw a SQL exception with the same state code signalling "
                    + "its yielding further retry attempts.",
            new String[] {"5", "10", "15", "20", "25"}),

    RETRY_MAX_BACKOFF_TIME(
            "retryMaxBackoffTime",
            "30s",
            false,
            "Maximum exponential backoff time per transaction in the format of a duration expression (like '12s')."
                    + "Applicable only when 'retryTransientErrors' is true.",
            new String[] {"5s", "7s", "15s", "30s", "1m"}),

    IMPLICIT_SELECT_FOR_UPDATE(
            "implicitSelectForUpdate",
            Boolean.FALSE.toString(),
            false,
            "The CockroachDB JDBC driver will automatically append a 'FOR UPDATE' clause to qualified SELECT statements (no "
                    + "aggregate functions, group by operators or internal schema). This will lock the rows returned by a selection "
                    + "query such that other transactions trying to access those rows are forced to wait for the "
                    + "transaction that locked the rows to finish. These other transactions are effectively put into "
                    + "a queue based on when they tried to read the value of the locked rows.",
            new String[] {"true", "false"}),

    USE_COCKROACH_METADATA(
            "useCockroachMetadata",
            Boolean.FALSE.toString(),
            false,
            "Use CockroachDB JDBC connection metadata rather than PostgreSQL. The latter may cause "
                    + "incompatibility with libraries binding to PostgreSQL version details, such as Flyway.",
            new String[] {"true", "false"}),

    REWRITE_BATCHED_INSERTS(
            "reWriteBatchedInserts",
            Boolean.FALSE.toString(),
            false,
            "Enable optimization to rewrite batch INSERT statements to use arrays.",
            new String[] {"true", "false"}),

    REWRITE_BATCHED_UPSERTS(
            "reWriteBatchedUpserts",
            Boolean.FALSE.toString(),
            false,
            "Enable optimization to rewrite batch UPSERT statements to use arrays.",
            new String[] {"true", "false"}),

    REWRITE_BATCHED_UPDATES(
            "reWriteBatchedUpdates",
            Boolean.FALSE.toString(),
            false,
            "Enable optimization to rewrite batch UPDATE statements to use arrays.",
            new String[] {"true", "false"})
    ;

    private final String name;

    private final String defaultValue;

    private final boolean required;

    private final String description;

    private final String[] choices;

    CockroachProperty(String name, String defaultValue, boolean required, String description, String[] choices) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.required = required;
        this.description = description;
        this.choices = choices;
    }

    public DriverPropertyInfo toDriverPropertyInfo(Properties properties) {
        java.sql.DriverPropertyInfo propertyInfo
                = new DriverPropertyInfo(name, properties.getProperty(name, defaultValue));
        propertyInfo.required = required;
        propertyInfo.description = description;
        propertyInfo.choices = choices;
        return propertyInfo;
    }

    public String getName() {
        return name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDescription() {
        return description;
    }

    public String[] getChoices() {
        return choices;
    }
}
