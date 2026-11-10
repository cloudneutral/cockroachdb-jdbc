package io.cockroachdb.jdbc;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.rewrite.BatchRewriteProcessor;

@Tags(value = {
        @Tag("unit-test")
})
public class CockroachPreparedBatchStatementTest {
    private static final String QUERY = "UPDATE product SET inventory = ?, price = ? WHERE id = ?";

    @Test
    public void whenSettingParametersOutOfOrder_expectArraysBoundInParameterOrder() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);

        Connection connectionMock = Mockito.mock(Connection.class);
        PreparedStatement batchStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(connectionMock.prepareStatement(batchQuery)).thenReturn(batchStatementMock);
        Mockito.when(batchStatementMock.executeUpdate()).thenReturn(2);

        List<String> arrayTypes = new ArrayList<>();
        List<List<Object>> arrayValues = new ArrayList<>();
        List<Array> arrays = new ArrayList<>();
        Mockito.when(connectionMock.createArrayOf(Mockito.anyString(), Mockito.any(Object[].class)))
                .thenAnswer(invocation -> {
                    arrayTypes.add(invocation.getArgument(0));
                    arrayValues.add(Arrays.asList((Object[]) invocation.getArgument(1)));
                    Array array = Mockito.mock(Array.class);
                    arrays.add(array);
                    return array;
                });

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);

        // Reverse order
        ps.setLong(3, 100L);
        ps.setString(2, "9.99");
        ps.setInt(1, 10);
        ps.addBatch();

        // Mixed order
        ps.setString(2, "19.99");
        ps.setLong(3, 200L);
        ps.setInt(1, 20);
        ps.addBatch();

        ps.executeBatch();

        Assertions.assertEquals(List.of("INTEGER", "VARCHAR", "BIGINT"), arrayTypes);
        Assertions.assertEquals(List.of(10, 20), arrayValues.get(0));
        Assertions.assertEquals(List.of("9.99", "19.99"), arrayValues.get(1));
        Assertions.assertEquals(List.of(100L, 200L), arrayValues.get(2));

        Mockito.verify(batchStatementMock).setArray(1, arrays.get(0));
        Mockito.verify(batchStatementMock).setArray(2, arrays.get(1));
        Mockito.verify(batchStatementMock).setArray(3, arrays.get(2));
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(QUERY);
    }

    @Test
    public void whenOverwritingParameter_expectLastValueWins() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);

        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.prepareStatement(batchQuery)).thenReturn(Mockito.mock(PreparedStatement.class));

        List<List<Object>> arrayValues = new ArrayList<>();
        Mockito.when(connectionMock.createArrayOf(Mockito.anyString(), Mockito.any(Object[].class)))
                .thenAnswer(invocation -> {
                    arrayValues.add(Arrays.asList((Object[]) invocation.getArgument(1)));
                    return Mockito.mock(Array.class);
                });

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        ps.setInt(1, 1);
        ps.setString(2, "a");
        ps.setLong(3, 3L);
        ps.setInt(1, 42);
        ps.addBatch();
        ps.executeBatch();

        Assertions.assertEquals(List.of(42), arrayValues.get(0));
        Assertions.assertEquals(List.of("a"), arrayValues.get(1));
        Assertions.assertEquals(List.of(3L), arrayValues.get(2));
    }

    @Test
    public void whenParameterNotSet_expectSQLExceptionOnAddBatch() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);

        Connection connectionMock = Mockito.mock(Connection.class);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        ps.setInt(1, 10);
        ps.setLong(3, 100L);

        SQLException ex = Assertions.assertThrows(SQLException.class, ps::addBatch);
        Assertions.assertEquals("22023", ex.getSQLState());
    }

    @Test
    public void whenInvalidatingRewriteWithOutOfOrderParameters_expectAllReplayed() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);

        Connection connectionMock = Mockito.mock(Connection.class);
        PreparedStatement delegateMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(connectionMock.prepareStatement(QUERY)).thenReturn(delegateMock);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        ps.setLong(3, 100L);
        ps.setInt(1, 10);
        ps.setString(2, "9.99");
        ps.executeUpdate(); // voids the batch rewrite and replays deferred setters

        Mockito.verify(delegateMock).setInt(1, 10);
        Mockito.verify(delegateMock).setString(2, "9.99");
        Mockito.verify(delegateMock).setLong(3, 100L);
        Mockito.verify(delegateMock).executeUpdate();
    }

    @Test
    public void whenGetResultSetOnBatchStatement_expectNoReExecution() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);

        Connection connectionMock = Mockito.mock(Connection.class);
        PreparedStatement delegateMock = Mockito.mock(PreparedStatement.class);
        ResultSet resultSetMock = Mockito.mock(ResultSet.class);
        Mockito.when(connectionMock.prepareStatement(QUERY)).thenReturn(delegateMock);
        Mockito.when(delegateMock.execute()).thenReturn(true);
        Mockito.when(delegateMock.getResultSet()).thenReturn(resultSetMock);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        ps.setInt(1, 10);
        ps.setString(2, "9.99");
        ps.setLong(3, 100L);

        Assertions.assertTrue(ps.execute());
        ResultSet rs = ps.getResultSet();
        Assertions.assertNotNull(rs);
        rs.next();

        Mockito.verify(delegateMock, Mockito.times(1)).execute();
        Mockito.verify(delegateMock, Mockito.never()).executeQuery();
        Mockito.verify(resultSetMock, Mockito.times(1)).next();
    }

    @Test
    public void whenGetResultSetOnPreparedStatement_expectNoReExecution() throws SQLException {
        PreparedStatement delegateMock = Mockito.mock(PreparedStatement.class);
        ResultSet resultSetMock = Mockito.mock(ResultSet.class);
        Mockito.when(delegateMock.execute()).thenReturn(true);
        Mockito.when(delegateMock.getResultSet()).thenReturn(resultSetMock);

        PreparedStatement ps = new CockroachPreparedStatement(delegateMock);
        Assertions.assertTrue(ps.execute());
        ResultSet rs = ps.getResultSet();
        Assertions.assertNotNull(rs);
        rs.next();

        Mockito.verify(delegateMock, Mockito.times(1)).execute();
        Mockito.verify(delegateMock, Mockito.never()).executeQuery();
        Mockito.verify(resultSetMock, Mockito.times(1)).next();
    }

    @Test
    public void whenGetResultSetReturnsNull_expectNull() throws SQLException {
        PreparedStatement delegateMock = Mockito.mock(PreparedStatement.class);
        PreparedStatement ps = new CockroachPreparedStatement(delegateMock);
        Assertions.assertNull(ps.getResultSet());
        Mockito.verify(delegateMock, Mockito.never()).executeQuery();
    }

    private static Connection arrayConnectionMock(String batchQuery, PreparedStatement batchStatementMock)
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.prepareStatement(batchQuery)).thenReturn(batchStatementMock);
        Mockito.when(connectionMock.createArrayOf(Mockito.anyString(), Mockito.any(Object[].class)))
                .thenAnswer(invocation -> Mockito.mock(Array.class));
        return connectionMock;
    }

    private static void addUpdateRows(PreparedStatement ps, int rows) throws SQLException {
        for (int i = 0; i < rows; i++) {
            ps.setInt(1, i);
            ps.setString(2, "9.99");
            ps.setLong(3, i);
            ps.addBatch();
        }
    }

    @Test
    public void whenUpdateBatchPartiallyMatches_expectOneUnknownCountPerCommand() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement batchStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(batchStatementMock.executeUpdate()).thenReturn(2);

        PreparedStatement ps = new CockroachPreparedBatchStatement(
                arrayConnectionMock(batchQuery, batchStatementMock), QUERY, batchQuery);
        addUpdateRows(ps, 3);

        Assertions.assertArrayEquals(new int[] {
                Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO
        }, ps.executeBatch());
    }

    @Test
    public void whenUpdateBatchMatchesNothing_expectZeroCountPerCommand() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement batchStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(batchStatementMock.executeUpdate()).thenReturn(0);

        PreparedStatement ps = new CockroachPreparedBatchStatement(
                arrayConnectionMock(batchQuery, batchStatementMock), QUERY, batchQuery);
        addUpdateRows(ps, 3);

        Assertions.assertArrayEquals(new int[] {0, 0, 0}, ps.executeBatch());
    }

    @Test
    public void whenUpdateBatchMatchesMoreRowsThanCommands_expectOneCountPerCommand() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement batchStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(batchStatementMock.executeLargeUpdate()).thenReturn(10L);

        PreparedStatement ps = new CockroachPreparedBatchStatement(
                arrayConnectionMock(batchQuery, batchStatementMock), QUERY, batchQuery);
        addUpdateRows(ps, 2);

        Assertions.assertArrayEquals(new long[] {Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO},
                ps.executeLargeBatch());
    }

    @Test
    public void whenInsertBatch_expectExactCountsWhenDeducible() throws SQLException {
        String insert = "INSERT INTO product (id, name) VALUES (?, ?)";
        String batchQuery = BatchRewriteProcessor.rewriteInsertStatement(insert);
        PreparedStatement batchStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(batchStatementMock.executeUpdate()).thenReturn(3);

        PreparedStatement ps = new CockroachPreparedBatchStatement(
                arrayConnectionMock(batchQuery, batchStatementMock), insert, batchQuery);
        for (int i = 0; i < 3; i++) {
            ps.setInt(1, i);
            ps.setString(2, "a");
            ps.addBatch();
        }
        Assertions.assertArrayEquals(new int[] {1, 1, 1}, ps.executeBatch());
    }

    @Test
    public void whenInsertOnConflictDoNothingPartiallyInserted_expectUnknownCounts() throws SQLException {
        String insert = "INSERT INTO product (id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING";
        String batchQuery = BatchRewriteProcessor.rewriteInsertStatement(insert);
        PreparedStatement batchStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(batchStatementMock.executeUpdate()).thenReturn(2);

        PreparedStatement ps = new CockroachPreparedBatchStatement(
                arrayConnectionMock(batchQuery, batchStatementMock), insert, batchQuery);
        for (int i = 0; i < 3; i++) {
            ps.setInt(1, i);
            ps.setString(2, "a");
            ps.addBatch();
        }
        Assertions.assertArrayEquals(new int[] {
                Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO
        }, ps.executeBatch());
    }

    @Test
    public void whenEmptyBatch_expectEmptyCountsAndNoExecution() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        Connection connectionMock = Mockito.mock(Connection.class);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);

        Assertions.assertArrayEquals(new int[0], ps.executeBatch());
        Assertions.assertArrayEquals(new long[0], ps.executeLargeBatch());
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(Mockito.anyString());
    }

    @Test
    public void whenBatchWithoutParameters_expectRegularBatchFallback() throws SQLException {
        String update = "UPDATE product SET inventory = 0 WHERE inventory < 0";
        Connection connectionMock = Mockito.mock(Connection.class);
        PreparedStatement delegateMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(connectionMock.prepareStatement(update)).thenReturn(delegateMock);
        Mockito.when(delegateMock.executeBatch()).thenReturn(new int[] {4, 0});

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, update, "n/a");
        ps.addBatch();
        ps.addBatch();

        Assertions.assertArrayEquals(new int[] {4, 0}, ps.executeBatch());
        Mockito.verify(delegateMock, Mockito.times(2)).addBatch();
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement("n/a");
    }

    @Test
    public void whenClearBatch_expectOnlySubsequentCommandsExecuted() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement batchStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(batchStatementMock.executeUpdate()).thenReturn(0);

        Connection connectionMock = arrayConnectionMock(batchQuery, batchStatementMock);
        List<Integer> arraySizes = new ArrayList<>();
        Mockito.when(connectionMock.createArrayOf(Mockito.anyString(), Mockito.any(Object[].class)))
                .thenAnswer(invocation -> {
                    arraySizes.add(((Object[]) invocation.getArgument(1)).length);
                    return Mockito.mock(Array.class);
                });

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        addUpdateRows(ps, 2);
        ps.clearBatch();
        addUpdateRows(ps, 1);

        Assertions.assertEquals(1, ps.executeBatch().length);
        Assertions.assertEquals(List.of(1, 1, 1), arraySizes);
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(QUERY);
    }

    @Test
    public void whenReusingStatementForSubsequentBatches_expectEachBatchRewritten() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement arrayStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(arrayStatementMock.executeUpdate()).thenReturn(0);

        Connection connectionMock = arrayConnectionMock(batchQuery, arrayStatementMock);
        List<Integer> arraySizes = new ArrayList<>();
        Mockito.when(connectionMock.createArrayOf(Mockito.anyString(), Mockito.any(Object[].class)))
                .thenAnswer(invocation -> {
                    arraySizes.add(((Object[]) invocation.getArgument(1)).length);
                    return Mockito.mock(Array.class);
                });

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);

        addUpdateRows(ps, 2);
        Assertions.assertEquals(2, ps.executeBatch().length);

        addUpdateRows(ps, 3);
        Assertions.assertEquals(3, ps.executeBatch().length);

        Assertions.assertEquals(List.of(2, 2, 2, 3, 3, 3), arraySizes);

        // Array statement prepared once and reused, scalar setters never reach it
        Mockito.verify(connectionMock, Mockito.times(1)).prepareStatement(batchQuery);
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(QUERY);
        Mockito.verify(arrayStatementMock, Mockito.times(2)).executeUpdate();
        Mockito.verify(arrayStatementMock, Mockito.never()).setInt(Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(arrayStatementMock, Mockito.never()).addBatch();
    }

    @Test
    public void whenSingleExecutionAfterBatch_expectOriginalStatement() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement arrayStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(arrayStatementMock.executeUpdate()).thenReturn(1);

        Connection connectionMock = arrayConnectionMock(batchQuery, arrayStatementMock);
        PreparedStatement originalMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(connectionMock.prepareStatement(QUERY)).thenReturn(originalMock);
        Mockito.when(originalMock.executeUpdate()).thenReturn(1);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        addUpdateRows(ps, 1);
        ps.executeBatch();

        ps.setInt(1, 7);
        ps.setString(2, "1.00");
        ps.setLong(3, 7L);
        Assertions.assertEquals(1, ps.executeUpdate());

        Mockito.verify(originalMock).setInt(1, 7);
        Mockito.verify(originalMock).setString(2, "1.00");
        Mockito.verify(originalMock).setLong(3, 7L);
        Mockito.verify(originalMock, Mockito.times(1)).executeUpdate();
        Mockito.verify(arrayStatementMock, Mockito.times(1)).executeUpdate();

        ps.close();
        Mockito.verify(arrayStatementMock).close();
        Mockito.verify(originalMock).close();
    }

    @Test
    public void whenGetWarningsAfterBatch_expectRewriteRetained() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement arrayStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(arrayStatementMock.executeUpdate()).thenReturn(1);

        Connection connectionMock = arrayConnectionMock(batchQuery, arrayStatementMock);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        Assertions.assertNull(ps.getWarnings());

        addUpdateRows(ps, 1);
        ps.executeBatch();
        ps.getWarnings();
        ps.clearWarnings();

        addUpdateRows(ps, 1);
        ps.executeBatch();

        Mockito.verify(arrayStatementMock).getWarnings();
        Mockito.verify(arrayStatementMock).clearWarnings();
        Mockito.verify(arrayStatementMock, Mockito.times(2)).executeUpdate();
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(QUERY);
    }

    @Test
    public void whenClosedInRewriteMode_expectNoOriginalStatementPrepared() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement arrayStatementMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(arrayStatementMock.executeUpdate()).thenReturn(1);

        Connection connectionMock = arrayConnectionMock(batchQuery, arrayStatementMock);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        addUpdateRows(ps, 1);
        ps.executeBatch();

        Assertions.assertFalse(ps.isClosed());
        ps.close();
        ps.close(); // idempotent
        Assertions.assertTrue(ps.isClosed());

        Mockito.verify(arrayStatementMock, Mockito.times(1)).close();
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(QUERY);

        SQLException ex = Assertions.assertThrows(SQLException.class, () -> ps.setInt(1, 1));
        Assertions.assertEquals("55000", ex.getSQLState());
        Assertions.assertThrows(SQLException.class, ps::executeBatch);
    }

    @Test
    public void whenClosedBeforeAnyExecution_expectNothingPrepared() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        Connection connectionMock = Mockito.mock(Connection.class);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        ps.close();

        Assertions.assertTrue(ps.isClosed());
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(Mockito.anyString());
    }

    @Test
    public void whenRewriteVoidedAfterAddBatch_expectPendingRowsReplayedOnOriginal() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        Connection connectionMock = Mockito.mock(Connection.class);
        PreparedStatement originalMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(connectionMock.prepareStatement(QUERY)).thenReturn(originalMock);
        Mockito.when(originalMock.executeBatch()).thenReturn(new int[] {1, 1, 1});

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        addUpdateRows(ps, 2);

        // Third row uses a setter that is not supported by the array rewrite
        java.sql.Timestamp ts = new java.sql.Timestamp(0);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        ps.setInt(1, 2);
        ps.setTimestamp(2, ts, cal);
        ps.setLong(3, 2L);
        ps.addBatch();

        Assertions.assertArrayEquals(new int[] {1, 1, 1}, ps.executeBatch());

        org.mockito.InOrder inOrder = Mockito.inOrder(originalMock);
        inOrder.verify(originalMock).setInt(1, 0);
        inOrder.verify(originalMock).setString(2, "9.99");
        inOrder.verify(originalMock).setLong(3, 0L);
        inOrder.verify(originalMock).addBatch();
        inOrder.verify(originalMock).setInt(1, 1);
        inOrder.verify(originalMock).setString(2, "9.99");
        inOrder.verify(originalMock).setLong(3, 1L);
        inOrder.verify(originalMock).addBatch();
        inOrder.verify(originalMock).setInt(1, 2);
        inOrder.verify(originalMock).setTimestamp(2, ts, cal);
        inOrder.verify(originalMock).setLong(3, 2L);
        inOrder.verify(originalMock).addBatch();
        inOrder.verify(originalMock).executeBatch();

        Mockito.verify(originalMock, Mockito.times(3)).addBatch();
        Mockito.verify(connectionMock, Mockito.never()).prepareStatement(batchQuery);
    }

    @Test
    public void whenRewriteVoidedByMetadataCallAfterAddBatch_expectNoRowsLost() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        Connection connectionMock = Mockito.mock(Connection.class);
        PreparedStatement originalMock = Mockito.mock(PreparedStatement.class);
        Mockito.when(connectionMock.prepareStatement(QUERY)).thenReturn(originalMock);
        Mockito.when(originalMock.executeBatch()).thenReturn(new int[] {1, 1});

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        addUpdateRows(ps, 2);
        ps.getMetaData();

        Assertions.assertEquals(2, ps.executeBatch().length);
        Mockito.verify(originalMock, Mockito.times(2)).addBatch();
        Mockito.verify(originalMock).setInt(1, 0);
        Mockito.verify(originalMock).setInt(1, 1);
    }

    @Test
    public void whenInconsistentParameterCountAcrossRows_expectSQLException() throws SQLException {
        String batchQuery = BatchRewriteProcessor.rewriteUpdateStatement(QUERY);
        PreparedStatement arrayStatementMock = Mockito.mock(PreparedStatement.class);
        Connection connectionMock = arrayConnectionMock(batchQuery, arrayStatementMock);

        PreparedStatement ps = new CockroachPreparedBatchStatement(connectionMock, QUERY, batchQuery);
        addUpdateRows(ps, 1);
        ps.setInt(1, 1);
        ps.setString(2, "x");
        ps.addBatch();

        SQLException ex = Assertions.assertThrows(SQLException.class, ps::executeBatch);
        Assertions.assertEquals("22023", ex.getSQLState());
        Mockito.verify(arrayStatementMock, Mockito.never()).executeUpdate();
    }
}
