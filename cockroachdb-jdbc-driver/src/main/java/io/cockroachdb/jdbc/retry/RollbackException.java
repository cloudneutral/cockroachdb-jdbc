package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;

import io.cockroachdb.jdbc.NonTransientCockroachException;
import io.cockroachdb.jdbc.util.ExceptionUtils;

/**
 * Exception thrown if a transaction could not be rolled back prior
 * to a transaction retry attempt.
 *
 * @author Kai Niemi
 */
public class RollbackException extends NonTransientCockroachException {
    public RollbackException(String reason, SQLException cause) {
        super(reason, ExceptionUtils.toPSQLState(cause.getSQLState()), cause);
    }
}
