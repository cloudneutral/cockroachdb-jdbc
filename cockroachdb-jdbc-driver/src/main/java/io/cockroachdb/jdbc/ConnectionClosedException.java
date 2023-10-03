package io.cockroachdb.jdbc;

import org.postgresql.util.PSQLState;

public class ConnectionClosedException extends NonTransientCockroachException {
    public ConnectionClosedException() {
        super("This connection has been closed", PSQLState.CONNECTION_DOES_NOT_EXIST);
    }
}
