package io.cockroachdb.jdbc;

import org.postgresql.util.PSQLState;

/**
 * Exception thrown when a {@code java.sql.Connection} is
 * accessed in an invalid state.
 *
 * @author Kai Niemi
 */
public class ConnectionInvalidException extends NonTransientCockroachException {
    public ConnectionInvalidException(String reason, PSQLState state) {
        super(reason, state);
    }
}
