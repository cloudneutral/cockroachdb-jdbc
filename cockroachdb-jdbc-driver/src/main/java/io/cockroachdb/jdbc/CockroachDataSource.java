package io.cockroachdb.jdbc;

import java.io.Closeable;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import javax.sql.CommonDataSource;
import javax.sql.DataSource;

import org.postgresql.PGProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cockroachdb.jdbc.util.DurationFormat;

/**
 * A simple, non-pooled {@code java.sql.DataSource} implementation for CockroachDB.
 */
public class CockroachDataSource implements DataSource, CommonDataSource, Closeable {
    @FunctionalInterface
    public interface DataSourceConfig {
        void addDataSourceProperty(String key, Object value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean autoCommit;

        private String url;

        private String username;

        private String password;

        private final Map<String, Object> properties = new LinkedHashMap<>();

        private boolean rewriteBatchedInserts
                = Boolean.parseBoolean(PGProperty.REWRITE_BATCHED_INSERTS.getDefaultValue());

        private boolean retryTransientErrors
                = Boolean.parseBoolean(CockroachProperty.RETRY_TRANSIENT_ERRORS.getDefaultValue());

        private boolean retryConnectionErrors
                = Boolean.parseBoolean(CockroachProperty.RETRY_CONNECTION_ERRORS.getDefaultValue());

        private boolean implicitSelectForUpdate
                = Boolean.parseBoolean(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getDefaultValue());

        private int retryMaxAttempts
                = Integer.parseInt(CockroachProperty.RETRY_MAX_ATTEMPTS.getDefaultValue());

        private long retryMaxBackoffTime
                = DurationFormat.parseDuration(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getDefaultValue()).toMillis();

        private String retryStrategyClassName
                = CockroachProperty.RETRY_STRATEGY_CLASSNAME.getDefaultValue();

        private Builder() {
        }

        public Builder withAutoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        public Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder withRetryTransientErrors(boolean retryTransientErrors) {
            this.retryTransientErrors = retryTransientErrors;
            return this;
        }

        public Builder withRetryConnectionErrors(boolean retryConnectionErrors) {
            this.retryConnectionErrors = retryConnectionErrors;
            return this;
        }

        public Builder withImplicitSelectForUpdate(boolean implicitSelectForUpdate) {
            this.implicitSelectForUpdate = implicitSelectForUpdate;
            return this;
        }

        public Builder withRewriteBatchedInserts(boolean rewriteBatchedInserts) {
            this.rewriteBatchedInserts = rewriteBatchedInserts;
            return this;
        }

        public Builder withRetryMaxAttempts(int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
            return this;
        }

        public Builder withRetryMaxBackoffTime(long retryMaxBackoffTime) {
            this.retryMaxBackoffTime = retryMaxBackoffTime;
            return this;
        }

        public Builder withRetryStrategyClassName(String retryStrategyClassName) {
            this.retryStrategyClassName = retryStrategyClassName;
            return this;
        }

        public Builder withDataSourceProperties(Consumer<DataSourceConfig> configurer) {
            configurer.accept(properties::put);
            return this;
        }

        public DataSource build() {
            CockroachDataSource ds = new CockroachDataSource();
            ds.setUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setAutoCommit(autoCommit);

            ds.addDataSourceProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(),
                    rewriteBatchedInserts);
            ds.addDataSourceProperty(CockroachProperty.RETRY_TRANSIENT_ERRORS.getName(),
                    retryTransientErrors);
            ds.addDataSourceProperty(CockroachProperty.RETRY_CONNECTION_ERRORS.getName(),
                    retryConnectionErrors);
            ds.addDataSourceProperty(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getName(),
                    implicitSelectForUpdate);
            ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(),
                    retryMaxAttempts);
            ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(),
                    retryMaxBackoffTime);
            ds.addDataSourceProperty(CockroachProperty.RETRY_STRATEGY_CLASSNAME.getName(),
                    retryStrategyClassName);

            properties.forEach(ds::addDataSourceProperty);

            return ds;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String url;

    private String username;

    private String password;

    private boolean autoCommit = true;

    private final Properties properties = new Properties();

    public CockroachDataSource() {
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public void addDataSourceProperty(String propertyName, Object value) {
        properties.put(propertyName, String.valueOf(value));
    }

    public String getDescription() {
        return "Non-Pooling DataSource from " + CockroachDriverInfo.DRIVER_FULL_NAME;
    }

    @Override
    public void close() {
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(username, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        try {
            CockroachDriver driver = CockroachDriver.getRegisteredDriver();
            if (username != null) {
                properties.put("user", username);
            }
            if (password != null) {
                properties.put("password", password);
            }
            Connection connection = driver.connect(url, properties);
            if (!autoCommit) {
                connection.setAutoCommit(false);
            }
            logger.debug("Created a {} for {} at {}", getDescription(), username, url);
            return connection;
        } catch (SQLException e) {
            logger.warn("Failed to create a {} for {} at {}: {}", getDescription(), username, url, e);
            throw e;
        }
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
        PGProperty.LOGIN_TIMEOUT.set(properties, seconds);
    }

    @Override
    public int getLoginTimeout() {
        return PGProperty.LOGIN_TIMEOUT.getIntNoCheck(properties);
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
        return java.util.logging.Logger.getLogger("io.cockroachdb.jdbc");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isAssignableFrom(getClass());
    }
}
