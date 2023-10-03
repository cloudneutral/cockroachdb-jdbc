package io.cockroachdb.jdbc;

import org.postgresql.util.PSQLState;

public class InvalidConnectionException extends NonTransientCockroachException {
    public InvalidConnectionException(String reason, PSQLState state) {
        super(reason, state);
    }
}
