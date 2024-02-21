package io.cockroachdb.jdbc.retry;

import org.postgresql.util.PSQLState;

import io.cockroachdb.jdbc.NonTransientCockroachException;

/**
 * Exception thrown if a transaction could not serialize due
 * to a concurrent update (checksum failure).
 *
 * @author Kai Niemi
 */
public class ConcurrentUpdateException extends NonTransientCockroachException {
    public ConcurrentUpdateException(String reason) {
        super(reason, PSQLState.SERIALIZATION_FAILURE);
    }
}
