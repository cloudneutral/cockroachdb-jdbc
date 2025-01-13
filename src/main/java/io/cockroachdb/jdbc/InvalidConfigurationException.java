package io.cockroachdb.jdbc;

import org.postgresql.util.PSQLState;

/**
 * Exception thrown when a malformed or missing driver configuration
 * setting is encountered.
 *
 * @author Kai Niemi
 */
public class InvalidConfigurationException extends NonTransientCockroachException {
    public InvalidConfigurationException(String msg, PSQLState state) {
        super(msg, state);
    }

    public InvalidConfigurationException(String msg, PSQLState state, Throwable cause) {
        super(msg, state, cause);
    }
}
