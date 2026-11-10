package io.cockroachdb.jdbc.retry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.ConnectionSettings;
import io.cockroachdb.jdbc.rewrite.QueryProcessor;

/**
 * Verifies that driver-level retries are confined to explicit transactions. In auto-commit
 * mode, every statement is committed implicitly, so replaying history would duplicate
 * already committed side effects.
 */
@Tags(value = {
        @Tag("unit-test")
})
public class AutoCommitRetryTest {
    private static final String INSERT = "insert into t (id) values (1)";

    private static final String UPDATE = "update t set x = x + 1 where id = 1";

    private static ConnectionSettings settings() {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(3);
        strategy.setMaxBackoffTime(Duration.ZERO);

        return new ConnectionSettings()
                .setRetryStrategy(strategy)
                .setRetryListener(Mockito.mock(RetryListener.class))
                .setQueryProcessor(QueryProcessor.PASS_THROUGH);
    }

    @Test
    public void whenAutoCommitAndSQLException40001_expectNoRetryAndNoReplay() throws SQLException {
        Connection primaryMock = Mockito.mock(Connection.class);
        Mockito.when(primaryMock.getAutoCommit()).thenReturn(true);

        Statement statementMock = Mockito.mock(Statement.class);
        Mockito.when(primaryMock.createStatement()).thenReturn(statementMock);
        Mockito.when(statementMock.executeUpdate(INSERT)).thenReturn(1);
        SQLException conflict = new SQLException("Disturbance!", "40001");
        Mockito.when(statementMock.executeUpdate(UPDATE)).thenThrow(conflict);

        Connection proxy = ConnectionRetryInterceptor.proxy(primaryMock, settings(), () -> {
            Assertions.fail("No new connection expected in auto-commit mode");
            return null;
        });

        Statement statement = proxy.createStatement();
        Assertions.assertEquals(1, statement.executeUpdate(INSERT));

        SQLException thrown = Assertions.assertThrows(SQLException.class, () -> statement.executeUpdate(UPDATE));
        Assertions.assertSame(conflict, thrown);

        Mockito.verify(statementMock, Mockito.times(1)).executeUpdate(INSERT);
        Mockito.verify(statementMock, Mockito.times(1)).executeUpdate(UPDATE);
        Mockito.verify(primaryMock, Mockito.never()).rollback();
        Mockito.verify(primaryMock, Mockito.never()).close();
    }

    @Test
    public void whenSwitchingToAutoCommit_expectNoRetry() throws SQLException {
        Connection primaryMock = Mockito.mock(Connection.class);
        Mockito.when(primaryMock.getAutoCommit()).thenReturn(false);

        Statement statementMock = Mockito.mock(Statement.class);
        Mockito.when(primaryMock.createStatement()).thenReturn(statementMock);
        Mockito.when(statementMock.executeUpdate(UPDATE)).thenThrow(new SQLException("Disturbance!", "40001"));

        Connection proxy = ConnectionRetryInterceptor.proxy(primaryMock, settings(), () -> {
            Assertions.fail("No new connection expected in auto-commit mode");
            return null;
        });

        proxy.setAutoCommit(true);

        Statement statement = proxy.createStatement();
        SQLException thrown = Assertions.assertThrows(SQLException.class, () -> statement.executeUpdate(UPDATE));
        Assertions.assertEquals("40001", thrown.getSQLState());
        Assertions.assertFalse(thrown instanceof TooManyRetriesException);

        Mockito.verify(statementMock, Mockito.times(1)).executeUpdate(UPDATE);
    }

    @Test
    public void whenExplicitTransactionAndSQLException40001_expectReplayOnNewConnection() throws SQLException {
        Connection primaryMock = Mockito.mock(Connection.class);
        Mockito.when(primaryMock.getAutoCommit()).thenReturn(false);
        Statement primaryStatementMock = Mockito.mock(Statement.class);
        Mockito.when(primaryMock.createStatement()).thenReturn(primaryStatementMock);
        Mockito.when(primaryStatementMock.executeUpdate(INSERT)).thenReturn(1);
        Mockito.when(primaryStatementMock.executeUpdate(UPDATE)).thenThrow(new SQLException("Disturbance!", "40001"));

        Connection retryMock = Mockito.mock(Connection.class);
        Mockito.when(retryMock.getAutoCommit()).thenReturn(false);
        Mockito.when(retryMock.isValid(Mockito.anyInt())).thenReturn(true);
        Statement retryStatementMock = Mockito.mock(Statement.class);
        Mockito.when(retryMock.createStatement()).thenReturn(retryStatementMock);
        Mockito.when(retryStatementMock.executeUpdate(INSERT)).thenReturn(1);
        Mockito.when(retryStatementMock.executeUpdate(UPDATE)).thenReturn(1);

        Connection proxy = ConnectionRetryInterceptor.proxy(primaryMock, settings(), () -> retryMock);

        Statement statement = proxy.createStatement();
        Assertions.assertEquals(1, statement.executeUpdate(INSERT));
        Assertions.assertEquals(1, statement.executeUpdate(UPDATE));
        proxy.commit();

        Mockito.verify(primaryMock, Mockito.times(1)).rollback();
        Mockito.verify(primaryMock, Mockito.times(1)).close();
        Mockito.verify(retryStatementMock, Mockito.times(1)).executeUpdate(INSERT);
        Mockito.verify(retryStatementMock, Mockito.times(1)).executeUpdate(UPDATE);
        Mockito.verify(retryMock, Mockito.times(1)).commit();
    }
}
