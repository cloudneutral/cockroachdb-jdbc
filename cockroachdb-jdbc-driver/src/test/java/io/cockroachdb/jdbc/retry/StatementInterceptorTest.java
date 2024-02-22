package io.cockroachdb.jdbc.retry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.ConnectionSettings;

@Tags(value = {
        @Tag("all-test"),
        @Tag("unit-test")
})
public class StatementInterceptorTest {
    @Test
    public void whenProxyingStatementMethods_expectPassThroughToDelegate() throws SQLException {
        Statement statementMock = Mockito.mock(Statement.class);
        ConnectionRetryInterceptor connectionRetryInterceptorMock = Mockito.mock(ConnectionRetryInterceptor.class);
        Mockito.when(connectionRetryInterceptorMock.getConnectionSettings())
                .thenReturn(new ConnectionSettings());

        Statement proxy = StatementRetryInterceptor.proxy(statementMock, connectionRetryInterceptorMock);

        proxy.execute("update x=y");
        proxy.executeQuery("select 1");
        proxy.close();

        Mockito.verify(statementMock, Mockito.times(1)).execute(Mockito.anyString());
        Mockito.verify(statementMock, Mockito.times(1)).executeQuery(Mockito.anyString());
        Mockito.verify(statementMock, Mockito.times(1)).close();
    }

    @Test
    public void whenUpdateThrowsSQLException40001_expectRetryAttempts() throws Throwable {
        final int retrys = 3;

        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(retrys);

        ConnectionSettings settings = new ConnectionSettings();
        settings.setRetryStrategy(strategy);
        settings.setRetryListener(properties -> {
        });

        Connection retryConnectionMock = Mockito.mock(Connection.class);
        Mockito.when(retryConnectionMock.isValid(Mockito.anyInt())).thenReturn(true);

        Connection connectionMock = Mockito.mock(Connection.class);
        ConnectionRetryInterceptor retryProxyStub = new ConnectionRetryInterceptor(connectionMock, settings, () -> {
            return retryConnectionMock;
        });

        Statement statementMock = Mockito.mock(Statement.class);
        Mockito.when(statementMock.executeQuery("select 1"))
                .thenThrow(new SQLException("Disturbance!", "40001"));

        Statement proxy = StatementRetryInterceptor.proxy(statementMock, retryProxyStub);

        Assertions.assertThrows(TooManyRetriesException.class, () -> proxy.executeQuery("select 1"));

        Mockito.verify(statementMock, Mockito.times(retrys + 1)).executeQuery("select 1");
    }
}
