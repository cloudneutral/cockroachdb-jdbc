package io.cockroachdb.jdbc.retry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.ConnectionSettings;
import io.cockroachdb.jdbc.rewrite.sfu.QueryProcessor;

@Tag("unit-test")
public class ConnectionRetryInterceptorTest {
    @Test
    public void whenProxyingConnectionMethods_expectPassThroughToDelegate() throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);

        PreparedStatement preparedStatementMock = Mockito.mock(PreparedStatement.class);
        Statement statementMock = Mockito.mock(Statement.class);

        Mockito.when(connectionMock.prepareStatement("select 1"))
                .thenReturn(preparedStatementMock);
        Mockito.when(connectionMock.createStatement())
                .thenReturn(statementMock);

        Connection proxy = ConnectionRetryInterceptor.proxy(connectionMock,
                new ConnectionSettings()
                        .setRetryListener(Mockito.mock(RetryListener.class))
                        .setRetryStrategy(Mockito.mock(RetryStrategy.class))
                        .setQueryProcessor(Mockito.mock(QueryProcessor.class)),
                () -> {
                    Assertions.fail();
                    return null;
                });

        proxy.commit();
        proxy.setAutoCommit(false);
        proxy.rollback();
        proxy.close();
        proxy.prepareStatement("select 1");
        proxy.createStatement();
        proxy.isReadOnly();

        Mockito.verify(connectionMock, Mockito.times(1)).commit();
        Mockito.verify(connectionMock, Mockito.times(1)).setAutoCommit(false);
        Mockito.verify(connectionMock, Mockito.times(1)).rollback();
        Mockito.verify(connectionMock, Mockito.times(1)).close();
        Mockito.verify(connectionMock, Mockito.times(1)).prepareStatement(Mockito.anyString());
        Mockito.verify(connectionMock, Mockito.times(1)).createStatement();
        Mockito.verify(connectionMock, Mockito.times(1)).isReadOnly();
    }

    @Test
    public void whenCommitThrowsSQLException40001_expectRetryAttempts() throws SQLException {
        Connection primaryMock = Mockito.mock(Connection.class);
        Connection retryMock = Mockito.mock(Connection.class);

        Mockito.when(retryMock.isValid(Mockito.anyInt())).thenReturn(true);

        Mockito.doThrow(new SQLException("Disturbance!", "40001"))
                .when(primaryMock).commit();

        Mockito.doThrow(new SQLException("Disturbance!", "40001"))
                .when(retryMock).commit();

        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(5);

        ConnectionSettings settings = new ConnectionSettings();
        settings.setRetryStrategy(strategy);
        settings.setRetryListener(properties -> {
        });

        Connection proxy = ConnectionRetryInterceptor.proxy(primaryMock, settings, () -> retryMock);

        Assertions.assertThrows(TooManyRetriesException.class, () -> proxy.commit());

        Mockito.verify(primaryMock, Mockito.times(1)).commit();
        Mockito.verify(retryMock, Mockito.times(5)).commit();
    }

    @Test
    public void whenCommitThrowsSQLException40003_expectNoRetryAttempts() throws SQLException {
        Connection primaryMock = Mockito.mock(Connection.class);
        Connection retryMock = Mockito.mock(Connection.class);

        Mockito.when(retryMock.isValid(Mockito.anyInt())).thenReturn(true);

        Mockito.doThrow(new SQLException("Disturbance!", "40003"))
                .when(primaryMock).commit();

        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(5);

        ConnectionSettings settings = new ConnectionSettings();
        settings.setRetryStrategy(strategy);
        settings.setRetryListener(properties -> {
        });

        Connection proxy = ConnectionRetryInterceptor.proxy(primaryMock, settings, () -> retryMock);

        Assertions.assertThrows(SQLException.class, () -> proxy.commit());

        Mockito.verify(primaryMock, Mockito.times(1)).commit();
        Mockito.verify(retryMock, Mockito.times(0)).commit();
    }

    @Test
    public void whenRollbackThrowsSQLException_expectRetryToFail() throws SQLException {
        Connection primaryMock = Mockito.mock(Connection.class);
        Connection retryMock = Mockito.mock(Connection.class);

        Mockito.when(retryMock.isValid(Mockito.anyInt())).thenReturn(true);

        Mockito.doThrow(new SQLException("Disturbance!", "40001"))
                .when(primaryMock).commit();

        Mockito.doThrow(new SQLException("Rollback went horribly wrong!"))
                .when(primaryMock).rollback();

        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(5);

        ConnectionSettings settings = new ConnectionSettings();
        settings.setRetryStrategy(strategy);
        settings.setRetryListener(properties -> {
        });

        Connection proxy = ConnectionRetryInterceptor.proxy(primaryMock, settings, () -> retryMock);

        Assertions.assertThrows(RollbackException.class, () -> proxy.commit());

        Mockito.verify(primaryMock, Mockito.times(1)).commit();
        Mockito.verify(primaryMock, Mockito.times(1)).rollback();
        Mockito.verify(retryMock, Mockito.times(0)).commit();
    }
}
