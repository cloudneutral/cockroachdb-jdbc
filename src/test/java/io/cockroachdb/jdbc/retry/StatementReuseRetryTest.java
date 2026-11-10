package io.cockroachdb.jdbc.retry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.ConnectionSettings;
import io.cockroachdb.jdbc.rewrite.QueryProcessor;

/**
 * Verifies that statements outliving the transaction (or auto-commit mode) they were
 * created in are recreated on the new connection delegate when retrying.
 */
@Tags(value = {
        @Tag("unit-test")
})
public class StatementReuseRetryTest {
    private static final String SQL_A = "update a set x = ? where id = 1";

    private static final String SQL_B = "update b set y = 1 where id = 2";

    private Connection primaryMock;

    private Connection retryMock;

    private Connection proxy;

    @BeforeEach
    public void setup() throws SQLException {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();
        strategy.setMaxAttempts(3);
        strategy.setMaxBackoffTime(Duration.ZERO);

        ConnectionSettings settings = new ConnectionSettings()
                .setRetryStrategy(strategy)
                .setRetryListener(Mockito.mock(RetryListener.class))
                .setQueryProcessor(QueryProcessor.PASS_THROUGH);

        primaryMock = Mockito.mock(Connection.class);
        retryMock = Mockito.mock(Connection.class);
        Mockito.when(retryMock.isValid(Mockito.anyInt())).thenReturn(true);

        proxy = ConnectionRetryInterceptor.proxy(primaryMock, settings, () -> retryMock);
    }

    private static SQLException conflict() {
        return new SQLException("Disturbance!", "40001");
    }

    @Test
    public void whenStatementReusedAcrossTransactions_expectRecreatedWithCurrentState() throws SQLException {
        PreparedStatement first = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_A)).thenReturn(first);
        Mockito.when(first.executeUpdate()).thenReturn(1).thenThrow(conflict());

        PreparedStatement second = Mockito.mock(PreparedStatement.class);
        Mockito.when(retryMock.prepareStatement(SQL_A)).thenReturn(second);
        Mockito.when(second.executeUpdate()).thenReturn(1);

        PreparedStatement ps = proxy.prepareStatement(SQL_A);
        ps.setQueryTimeout(5);

        // Transaction 1
        ps.setInt(1, 1);
        Assertions.assertEquals(1, ps.executeUpdate());
        proxy.commit();

        // Transaction 2
        ps.setInt(1, 2);
        Assertions.assertEquals(1, ps.executeUpdate());
        proxy.commit();

        Mockito.verify(retryMock, Mockito.times(1)).prepareStatement(SQL_A);

        InOrder inOrder = Mockito.inOrder(second);
        inOrder.verify(second).setQueryTimeout(5);
        inOrder.verify(second).setInt(1, 1);
        inOrder.verify(second).setInt(1, 2);
        inOrder.verify(second).executeUpdate();

        // Execution from the committed transaction 1 must not be replayed
        Mockito.verify(second, Mockito.times(1)).executeUpdate();
        Mockito.verify(retryMock, Mockito.times(1)).commit();
    }

    @Test
    public void whenStatementCreatedInAutoCommitMode_expectRecreatedInTransaction() throws SQLException {
        Mockito.when(primaryMock.getAutoCommit()).thenReturn(true);

        PreparedStatement first = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_A)).thenReturn(first);
        Mockito.when(first.executeUpdate()).thenReturn(1).thenThrow(conflict());

        PreparedStatement second = Mockito.mock(PreparedStatement.class);
        Mockito.when(retryMock.prepareStatement(SQL_A)).thenReturn(second);
        Mockito.when(second.executeUpdate()).thenReturn(1);

        PreparedStatement ps = proxy.prepareStatement(SQL_A);
        ps.setFetchSize(50);
        ps.setString(1, "a");
        Assertions.assertEquals(1, ps.executeUpdate()); // auto-committed

        proxy.setAutoCommit(false);
        ps.setString(1, "b");
        Assertions.assertEquals(1, ps.executeUpdate());

        InOrder inOrder = Mockito.inOrder(second);
        inOrder.verify(second).setFetchSize(50);
        inOrder.verify(second).setString(1, "a");
        inOrder.verify(second).setString(1, "b");
        inOrder.verify(second).executeUpdate();

        Mockito.verify(second, Mockito.times(1)).executeUpdate();
    }

    @Test
    public void whenStatementClosedInPreviousTransaction_expectNotRecreated() throws SQLException {
        PreparedStatement statementA = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_A)).thenReturn(statementA);
        PreparedStatement statementB = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_B)).thenReturn(statementB);
        Mockito.when(statementB.executeUpdate()).thenThrow(conflict());

        PreparedStatement retryB = Mockito.mock(PreparedStatement.class);
        Mockito.when(retryMock.prepareStatement(SQL_B)).thenReturn(retryB);

        PreparedStatement a = proxy.prepareStatement(SQL_A);
        a.setInt(1, 1);
        a.executeUpdate();
        a.close();
        proxy.commit();

        PreparedStatement b = proxy.prepareStatement(SQL_B);
        b.executeUpdate();

        Mockito.verify(retryMock, Mockito.never()).prepareStatement(SQL_A);
        Mockito.verify(retryMock, Mockito.times(1)).prepareStatement(SQL_B);
        Mockito.verify(retryB, Mockito.times(1)).executeUpdate();
    }

    @Test
    public void whenStatementFromPreviousTransactionClosedInCurrent_expectReplayed() throws SQLException {
        PreparedStatement statementA = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_A)).thenReturn(statementA);
        PreparedStatement statementB = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_B)).thenReturn(statementB);
        Mockito.when(statementB.executeUpdate()).thenThrow(conflict());

        PreparedStatement retryA = Mockito.mock(PreparedStatement.class);
        Mockito.when(retryMock.prepareStatement(SQL_A)).thenReturn(retryA);
        PreparedStatement retryB = Mockito.mock(PreparedStatement.class);
        Mockito.when(retryMock.prepareStatement(SQL_B)).thenReturn(retryB);

        PreparedStatement a = proxy.prepareStatement(SQL_A);
        proxy.commit();

        a.setInt(1, 5);
        a.executeUpdate();
        a.close();

        PreparedStatement b = proxy.prepareStatement(SQL_B);
        b.executeUpdate();

        InOrder inOrder = Mockito.inOrder(retryA);
        inOrder.verify(retryA).setInt(1, 5);
        inOrder.verify(retryA).executeUpdate();
        inOrder.verify(retryA).close();
        Mockito.verify(retryB, Mockito.times(1)).executeUpdate();
    }

    @Test
    public void whenPendingBatchSpansCommit_expectPendingEntriesReplayed() throws SQLException {
        PreparedStatement first = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_A)).thenReturn(first);
        Mockito.when(first.executeBatch()).thenThrow(conflict());

        PreparedStatement second = Mockito.mock(PreparedStatement.class);
        Mockito.when(retryMock.prepareStatement(SQL_A)).thenReturn(second);
        Mockito.when(second.executeBatch()).thenReturn(new int[] {1, 1});

        PreparedStatement ps = proxy.prepareStatement(SQL_A);
        ps.setInt(1, 1);
        ps.addBatch();
        proxy.commit();

        ps.setInt(1, 2);
        ps.addBatch();
        Assertions.assertArrayEquals(new int[] {1, 1}, ps.executeBatch());

        InOrder inOrder = Mockito.inOrder(second);
        inOrder.verify(second).setInt(1, 1);
        inOrder.verify(second).addBatch();
        inOrder.verify(second).setInt(1, 2);
        inOrder.verify(second).addBatch();
        inOrder.verify(second).executeBatch();
        Mockito.verify(second, Mockito.times(2)).addBatch();
    }

    @Test
    public void whenConnectionClosed_expectStatementsReleased() throws SQLException {
        PreparedStatement first = Mockito.mock(PreparedStatement.class);
        Mockito.when(primaryMock.prepareStatement(SQL_A)).thenReturn(first);

        proxy.prepareStatement(SQL_A);
        proxy.close();

        ConnectionRetryInterceptor interceptor
                = (ConnectionRetryInterceptor) java.lang.reflect.Proxy.getInvocationHandler(proxy);
        Assertions.assertEquals(0, interceptor.openStatementCount());
    }
}
