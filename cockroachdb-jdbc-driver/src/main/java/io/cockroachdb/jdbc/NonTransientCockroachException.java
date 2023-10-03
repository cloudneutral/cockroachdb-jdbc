package io.cockroachdb.jdbc;

import java.sql.SQLNonTransientException;

import org.postgresql.util.PSQLState;

/**
 * A CockroachDB JDBC driver specific non-transient SQL exception type.
 */
public abstract class NonTransientCockroachException extends SQLNonTransientException {
    public NonTransientCockroachException(String msg, PSQLState state) {
        super(msg, state == null ? null : state.getState());
    }

    public NonTransientCockroachException(String msg,
                                          PSQLState state,
                                          Throwable cause) {
        super(msg, state == null ? null : state.getState(), cause);
    }
}
