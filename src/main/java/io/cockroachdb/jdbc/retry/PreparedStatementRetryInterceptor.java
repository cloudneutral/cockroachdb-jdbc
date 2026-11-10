package io.cockroachdb.jdbc.retry;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * An interceptor for {@code java.sql.PreparedStatement} with retry capability.
 *
 * @author Kai Niemi
 */
public class PreparedStatementRetryInterceptor extends AbstractStatementRetryInterceptor<PreparedStatement> {
    public static PreparedStatement proxy(PreparedStatement delegate,
                                          ConnectionRetryInterceptor connectionInterceptor) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatementRetryInterceptor.class.getClassLoader(),
                new Class[] {PreparedStatement.class},
                new PreparedStatementRetryInterceptor(delegate, connectionInterceptor));
    }

    protected PreparedStatementRetryInterceptor(PreparedStatement delegate,
                                                ConnectionRetryInterceptor connectionRetryInterceptor) {
        super(delegate, connectionRetryInterceptor);
    }

    @Override
    protected ResultSet wrapResultSet(ResultSet resultSet) {
        return resultSet; // Already wrapped by CockroachPreparedStatement
    }
}
