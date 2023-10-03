package io.cockroachdb.jdbc.retry;

import org.postgresql.util.PSQLState;

import io.cockroachdb.jdbc.NonTransientCockroachException;

public class ConcurrentUpdateException extends NonTransientCockroachException {
    public ConcurrentUpdateException(String reason) {
        super(reason, PSQLState.SERIALIZATION_FAILURE);
    }
}
