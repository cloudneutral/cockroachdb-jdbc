package io.cockroachdb.jdbc;

import org.postgresql.util.PSQLState;

/**
 * Exception thrown when a closed {@code java.sql.Connection} has
 * been accessed in an invalid fashion.
 *
 * @author Kai Niemi
 */
public class ConnectionClosedException extends NonTransientCockroachException {
    public ConnectionClosedException() {
        super("This connection has been closed", PSQLState.CONNECTION_DOES_NOT_EXIST);
    }
}
