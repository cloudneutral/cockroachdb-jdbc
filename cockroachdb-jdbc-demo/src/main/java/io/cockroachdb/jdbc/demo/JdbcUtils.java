package io.cockroachdb.jdbc.demo;

import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.postgresql.util.PSQLState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cockroachdb.jdbc.util.ExceptionUtils;

public abstract class JdbcUtils {
    private static final Logger logger = LoggerFactory.getLogger(JdbcUtils.class);

    public static final int MAX_ATTEMPTS = 30;

    public static final int MAX_WAIT_MILLIS = 15000;

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    private JdbcUtils() {
    }

    public static <T> T select(DataSource dataSource, String sql,
                               ResultSetCallback<T> action)
            throws DataAccessException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    return action.process(rs);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException(ex);
        }
    }

    public static <T> T executeWithoutTransaction(DataSource ds,
                                                  ConnectionCallback<T> action) {
        try (Connection conn = ds.getConnection()) {
            T result;
            try {
                result = action.process(conn);
            } catch (RuntimeException | Error ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new UndeclaredThrowableException(ex,
                        "TransactionCallback threw undeclared checked exception");
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    public static <T> T executeWithinTransaction(DataSource ds,
                                                 ConnectionCallback<T> action) {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);

            T result;
            try {
                result = action.process(conn);
            } catch (RuntimeException | Error ex) {
                conn.rollback();
                throw ex;
            } catch (Throwable ex) {
                conn.rollback();
                throw new UndeclaredThrowableException(ex,
                        "TransactionCallback threw undeclared checked exception");
            }
            conn.commit();
            return result;
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    public static <T> T executeWithinTransactionWithRetry(DataSource ds,
                                                          ConnectionCallback<T> action) {
        Throwable initialEx = null;

        final Instant callTime = Instant.now();

        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                T rv = executeWithinTransaction(ds, action);
                if (attempt > 1) {
                    logger.info("Transient SQL exception recovered after attempt: {}, time spent retrying: {}",
                            attempt, Duration.between(callTime, Instant.now()));
                }
                return rv;
            } catch (Exception e) {
                Throwable throwable = ExceptionUtils.getMostSpecificCause(e);
                if (throwable instanceof SQLException && PSQLState.SERIALIZATION_FAILURE
                        .getState().equals(((SQLException) throwable).getSQLState())) {
                    if (initialEx == null) {
                        initialEx = throwable;
                    }
                    try {
                        Duration delay = Duration.ofMillis(
                                Math.min((long) (Math.pow(2, attempt) + 100) + RANDOM.nextLong(1000),
                                        MAX_WAIT_MILLIS));
                        logger.debug("Transient SQL exception detected - attempt: {}, sleeping {}: [{}]",
                                attempt, delay, throwable.toString());
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new DataAccessException("Exhausted all " + MAX_ATTEMPTS
                + " transaction retry attempts - giving up!", initialEx);
    }
}
