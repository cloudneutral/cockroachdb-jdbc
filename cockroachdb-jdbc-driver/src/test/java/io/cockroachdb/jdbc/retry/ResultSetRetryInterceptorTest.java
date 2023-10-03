package io.cockroachdb.jdbc.retry;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.ConnectionSettings;

@Tag("unit-test")
public class ResultSetRetryInterceptorTest {
    @Test
    public void whenProxyingResultSetMethods_expectPassThroughToDelegate() throws SQLException {
        ResultSet preparedStatementMock = Mockito.mock(ResultSet.class);
        ConnectionRetryInterceptor connectionRetryInterceptorMock = Mockito.mock(ConnectionRetryInterceptor.class);
        Mockito.when(connectionRetryInterceptorMock.getConnectionSettings())
                .thenReturn(new ConnectionSettings());

        ResultSet proxy = ResultSetRetryInterceptor.proxy(preparedStatementMock, connectionRetryInterceptorMock);

        proxy.next();
        proxy.getString(1);
        proxy.close();

        Mockito.verify(preparedStatementMock, Mockito.times(1)).next();
        Mockito.verify(preparedStatementMock, Mockito.times(1)).getString(Mockito.anyInt());
        Mockito.verify(preparedStatementMock, Mockito.times(1)).close();
    }

    @Test
    public void whenUpdateThrowsSQLException40001_expectRetryAttempts() throws Throwable {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(1);

        ConnectionSettings settings = new ConnectionSettings();
        settings.setRetryStrategy(strategy);
        settings.setRetryListener(properties -> {
        });

        ResultSet resultSetMock = Mockito.mock(ResultSet.class);
        Mockito.when(resultSetMock.next())
                .thenReturn(true)
                .thenReturn(true);
        Mockito.when(resultSetMock.getString(1))
                .thenReturn("Hello")
                .thenReturn("Hell0");

        PreparedStatement preparedStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(preparedStatementMock.executeQuery()).thenReturn(resultSetMock);

        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.isValid(Mockito.anyInt())).thenReturn(true);
        Mockito.when(connectionMock.prepareStatement(Mockito.anyString())).thenReturn(preparedStatementMock);

        ConnectionRetryInterceptor connectionRetryProxy = new ConnectionRetryInterceptor(connectionMock, settings,
                () -> {
                    return connectionMock;
                });

        Connection connectionProxy = (Connection) Proxy.newProxyInstance(
                ConnectionRetryInterceptor.class.getClassLoader(),
                new Class[] {Connection.class},
                connectionRetryProxy);

        ResultSet rs = connectionProxy.prepareStatement("select 1+2").executeQuery();

        rs.next();
        rs.getString(1);

        Assertions.assertThrows(SQLException.class, () -> connectionRetryProxy.retry(connectionMock));

        Mockito.verify(resultSetMock, Mockito.times(2)).next();
        Mockito.verify(resultSetMock, Mockito.times(2)).getString(Mockito.anyInt());
    }
}
