package io.cockroachdb.jdbc.query;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mockito;

import io.cockroachdb.jdbc.VariableSource;

@Tag("unit-test")
public class SelectForUpdateProcessorTest {
    public static final Stream<Arguments> queries = Stream.of(
            Arguments.of(false, "select sum(1+2)"),
            Arguments.of(false, "select a.id,a.name,sum(a.age) from x where 1=2"),
            Arguments.of(true, "select a.id,a.name,sun(a.age) from x where 1=2"),
            Arguments.of(false, "select a.id,a.name,sun(a.age) from x where 1=2 group by a.age"),
            Arguments.of(false, "select a.id,a.name,avg(a.age) from x where 1=2"),
            Arguments.of(false, "select a.id,a.name,avz(a.age) from x where 1=2 group by a.age"),
            Arguments.of(false, "select a.id,a.name,xxx(avg(a.age)) from x where 1=2"),
            Arguments.of(false, "select a.id,a.name,xxx(avg(a.age)) from x where 1=2 group by a.age"),
            Arguments.of(true, "select sun(4)"),
            Arguments.of(false, "select distinct a,b,c from y"),
            Arguments.of(false,
                    "select count(*) from (select distinct a.id from account a where a.id = gen_random_uuid())"),
            Arguments.of(true, "select all from y"),
            Arguments.of(true, "select * from account where id in (?, ?)"),
            Arguments.of(false, "select * from crdb_internal.x where id in (?)"),
            Arguments.of(false, "select * from information_schema.x where id in (?)"),
            Arguments.of(false, "select * from pg_catalog.x where id in (?)"),
            Arguments.of(false, "select * from pg_extension.x where id in (?)"),
            Arguments.of(false, "insert into t values (?,?)"),
            Arguments.of(false, "update t set a=?, b=? where 1=2"),
            Arguments.of(false, "delete from t where 1=2"),
            Arguments.of(false, "select * from x where id in (?) FOR UPDATE"),
            Arguments.of(false, "select * from x where id in (?) AS OF SYSTEM TIME follower_read_timestamp()"),
            Arguments.of(false, "select * from x where id in (?) AS OF SYSTEM TIME '-1h'")
    );

    @ParameterizedTest
    @VariableSource("queries")
    public void whenProcessingQuery_expectRuleBasedRewrite(boolean sfuExpected, String query)
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.isReadOnly())
                .thenReturn(false);

        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        if (sfuExpected) {
            Assertions.assertEquals(query + " FOR UPDATE", queryProcessor.processQuery(connectionMock, query));
        } else {
            Assertions.assertEquals(query, queryProcessor.processQuery(connectionMock, query));
        }

        Mockito.when(connectionMock.isReadOnly())
                .thenReturn(true);

        Assertions.assertEquals(query, queryProcessor.processQuery(connectionMock, query));
    }

    @Test
    public void whenProcessingQuery_withMalformedInput_expectNothing()
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);

        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> queryProcessor.processQuery(connectionMock, null));
        Assertions.assertEquals("", queryProcessor.processQuery(connectionMock, ""));
    }

    @Test
    public void whenProcessingQuery_withBrokenConnection_expectSQLException()
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.isReadOnly())
                .thenThrow(new SQLException("Disturbance!"));

        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        Assertions.assertThrows(SQLException.class,
                () -> queryProcessor.processQuery(connectionMock, "select 1+2"));
    }

    @Test
    public void whenProcessingQuery_withTerminator_expectSFUAppliedProperly()
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.isReadOnly())
                .thenReturn(false);

        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        Assertions.assertEquals("select 1+2 where id=1 FOR UPDATE",
                queryProcessor.processQuery(connectionMock, "select 1+2 where id=1"));
        Assertions.assertEquals("select 1+3 where id=1 FOR UPDATE;",
                queryProcessor.processQuery(connectionMock, "select 1+3 where id=1;"));
        Assertions.assertEquals("select 1+4 where id=1 FOR UPDATE;;",
                queryProcessor.processQuery(connectionMock, "select 1+4 where id=1;;"));
        Assertions.assertEquals("select 1+5 where id=1  FOR UPDATE;;",
                queryProcessor.processQuery(connectionMock, "select 1+5 where id=1 ;;"));
        Assertions.assertEquals("select 1+5 where id=1  FOR UPDATE; ;",
                queryProcessor.processQuery(connectionMock, "select 1+5 where id=1 ; ;"));
        Assertions.assertEquals("select 1+6 where id=1 FOR UpDaTe;;",
                queryProcessor.processQuery(connectionMock, "select 1+6 where id=1 FOR UpDaTe;;"));
    }

    @Test
    public void whenProcessingQuery_withAOST_expectNoSFUApplied()
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.isReadOnly())
                .thenReturn(false);

        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        Assertions.assertEquals("select 1+2 where id=1 FOR UPDATE",
                queryProcessor.processQuery(connectionMock, "select 1+2 where id=1"));
        Assertions.assertEquals("select 1+3 where id=1 AS OF SYSTEM TIME follower_read_timestamp();",
                queryProcessor.processQuery(connectionMock,
                        "select 1+3 where id=1 AS OF SYSTEM TIME follower_read_timestamp();"));
    }
}
