package io.cockroachdb.jdbc.demo;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
