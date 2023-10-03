package io.cockroachdb.jdbc.retry;

import java.sql.SQLException;

import io.cockroachdb.jdbc.NonTransientCockroachException;
import io.cockroachdb.jdbc.util.ExceptionUtils;

public class TooManyRetriesException extends NonTransientCockroachException {
    public TooManyRetriesException(String reason, SQLException cause) {
        super(reason, ExceptionUtils.toPSQLState(cause.getSQLState()), cause);
    }
}
