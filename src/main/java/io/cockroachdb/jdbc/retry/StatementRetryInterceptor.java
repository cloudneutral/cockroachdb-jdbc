package io.cockroachdb.jdbc.retry;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Statement;

import io.cockroachdb.jdbc.CockroachResultSet;

/**
 * An interceptor for {@code java.sql.Statement} with retry capability.
 *
 * @author Kai Niemi
 */
public class StatementRetryInterceptor extends AbstractStatementRetryInterceptor<Statement> {
    public static Statement proxy(Statement delegate, ConnectionRetryInterceptor connectionInterceptor) {
        return (Statement) Proxy.newProxyInstance(
                StatementRetryInterceptor.class.getClassLoader(),
                new Class[] {Statement.class}, new StatementRetryInterceptor(delegate, connectionInterceptor));
    }

    protected StatementRetryInterceptor(Statement delegate,
                                        ConnectionRetryInterceptor connectionRetryInterceptor) {
        super(delegate, connectionRetryInterceptor);
    }

    @Override
    protected ResultSet wrapResultSet(ResultSet resultSet) {
        return new CockroachResultSet(resultSet);
    }
}
