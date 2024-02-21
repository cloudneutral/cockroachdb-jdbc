package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;

import io.cockroachdb.jdbc.NonTransientCockroachException;
import io.cockroachdb.jdbc.util.ExceptionUtils;

/**
 * Exception thrown if the transaction retry limit has been reached.
 *
 * @author Kai Niemi
 */
public class TooManyRetriesException extends NonTransientCockroachException {
    public TooManyRetriesException(String reason, SQLException cause) {
        super(reason, ExceptionUtils.toPSQLState(cause.getSQLState()), cause);
    }
}
