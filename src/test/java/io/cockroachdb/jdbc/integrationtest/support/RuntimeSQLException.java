package io.cockroachdb.jdbc.integrationtest.support;

public class RuntimeSQLException extends RuntimeException {
    public RuntimeSQLException(String message) {
        super(message);
    }

    public RuntimeSQLException(Throwable cause) {
        super(cause);
    }

    public RuntimeSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
