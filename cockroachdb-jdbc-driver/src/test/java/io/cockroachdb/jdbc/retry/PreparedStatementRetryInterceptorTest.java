package io.cockroachdb.jdbc.retry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
public class PreparedStatementRetryInterceptorTest {
    @Test
    public void whenProxyingPreparedStatementMethods_expectPassThroughToDelegate() throws SQLException {
        PreparedStatement preparedStatementMock = Mockito.mock(PreparedStatement.class);
        ConnectionRetryInterceptor connectionRetryInterceptorMock = Mockito.mock(ConnectionRetryInterceptor.class);
        Mockito.when(connectionRetryInterceptorMock.getConnectionSettings())
                .thenReturn(new ConnectionSettings());

        PreparedStatement proxy = PreparedStatementRetryInterceptor.proxy(preparedStatementMock,
                connectionRetryInterceptorMock);

        proxy.executeUpdate();
        proxy.executeQuery();
        proxy.close();

        Mockito.verify(preparedStatementMock, Mockito.times(1)).executeQuery();
        Mockito.verify(preparedStatementMock, Mockito.times(1)).executeUpdate();
        Mockito.verify(preparedStatementMock, Mockito.times(1)).close();
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

        PreparedStatement preparedStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(preparedStatementMock.executeUpdate())
                .thenThrow(new SQLException("Disturbance!", "40001"));

        PreparedStatement proxy = PreparedStatementRetryInterceptor.proxy(preparedStatementMock, retryProxyStub);

        Assertions.assertThrows(TooManyRetriesException.class, () -> proxy.executeUpdate());

        Mockito.verify(preparedStatementMock, Mockito.times(retrys + 1)).executeUpdate();
    }
}
