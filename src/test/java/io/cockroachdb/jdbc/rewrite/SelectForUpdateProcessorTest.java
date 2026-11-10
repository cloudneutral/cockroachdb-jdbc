package io.cockroachdb.jdbc.rewrite;

import io.cockroachdb.jdbc.VariableSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import io.cockroachdb.jdbc.parser.CockroachSelectLexer;
import java.util.stream.Stream;

@Tags(value = {
        @Tag("unit-test")
})
public class SelectForUpdateProcessorTest {
    // Original rule-based cases. Expectations changed for function calls (any function call now
    // disqualifies), SELECT without FROM and the "ALL" quantifier without projection.
    public static final Stream<Arguments> queries = Stream.of(
            Arguments.of(false, "select sum(1+2)"),
            Arguments.of(false, "select a.id,a.name,sum(a.age) from x where 1=2"),
            Arguments.of(false, "select a.id,a.name,sun(a.age) from x where 1=2"),
            Arguments.of(false, "select a.id,a.name,sun(a.age) from x where 1=2 group by a.age"),
            Arguments.of(false, "select a.id,a.name,avg(a.age) from x where 1=2"),
            Arguments.of(false, "select a.id,a.name,avz(a.age) from x where 1=2 group by a.age"),
            Arguments.of(false, "select a.id,a.name,xxx(avg(a.age)) from x where 1=2"),
            Arguments.of(false, "select a.id,a.name,xxx(avg(a.age)) from x where 1=2 group by a.age"),
            Arguments.of(false, "select sun(4)"),
            Arguments.of(false, "select distinct a,b,c from y"),
            Arguments.of(false,
                    "select count(*) from (select distinct a.id from account a where a.id = gen_random_uuid())"),
            Arguments.of(false, "select all from y"),
            Arguments.of(true, "select * from account where id in (?, ?)"),
            Arguments.of(true, "select a.id,a.name from x a where 1=2"),
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
        Assertions.assertEquals("   ", queryProcessor.processQuery(connectionMock, "   "));
        Assertions.assertEquals(";", queryProcessor.processQuery(connectionMock, ";"));
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
    public void whenProcessingNonSelect_expectReadOnlyNotConsulted()
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        queryProcessor.processQuery(connectionMock, "update t set a = 1");
        queryProcessor.processQuery(connectionMock, "select 'unclosed");
        Mockito.verify(connectionMock, Mockito.never()).isReadOnly();
    }

    @Test
    public void whenProcessingQuery_withTerminator_expectSFUAppliedProperly()
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.isReadOnly())
                .thenReturn(false);

        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        Assertions.assertEquals("select 1+2 from t where id=1 FOR UPDATE",
                queryProcessor.processQuery(connectionMock, "select 1+2 from t where id=1"));
        Assertions.assertEquals("select 1+3 from t where id=1 FOR UPDATE;",
                queryProcessor.processQuery(connectionMock, "select 1+3 from t where id=1;"));
        Assertions.assertEquals("select 1+4 from t where id=1 FOR UPDATE;;",
                queryProcessor.processQuery(connectionMock, "select 1+4 from t where id=1;;"));
        Assertions.assertEquals("select 1+5 from t where id=1  FOR UPDATE;;",
                queryProcessor.processQuery(connectionMock, "select 1+5 from t where id=1 ;;"));
        Assertions.assertEquals("select 1+5 from t where id=1  FOR UPDATE; ;",
                queryProcessor.processQuery(connectionMock, "select 1+5 from t where id=1 ; ;"));
        Assertions.assertEquals("select 1+6 from t where id=1 FOR UpDaTe;;",
                queryProcessor.processQuery(connectionMock, "select 1+6 from t where id=1 FOR UpDaTe;;"));
    }

    @Test
    public void whenProcessingQuery_withAOST_expectNoSFUApplied()
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Mockito.when(connectionMock.isReadOnly())
                .thenReturn(false);

        QueryProcessor queryProcessor = new SelectForUpdateProcessor();
        Assertions.assertEquals("select 1+2 from t where id=1 FOR UPDATE",
                queryProcessor.processQuery(connectionMock, "select 1+2 from t where id=1"));
        Assertions.assertEquals("select 1+3 from t AS OF SYSTEM TIME follower_read_timestamp() where id=1;",
                queryProcessor.processQuery(connectionMock,
                        "select 1+3 from t AS OF SYSTEM TIME follower_read_timestamp() where id=1;"));
        Assertions.assertEquals("select 1+3 from t AS OF SYSTEM TIME '-10s' where id=1",
                queryProcessor.processQuery(connectionMock,
                        "select 1+3 from t AS OF SYSTEM TIME '-10s' where id=1"));
    }

    // Eligible queries: [query, expected rewrite]
    public static final Stream<Arguments> eligibleQueries = Stream.of(
            // Simple single-table selections
            Arguments.of("select * from account", "select * from account FOR UPDATE"),
            Arguments.of("SELECT * FROM account", "SELECT * FROM account FOR UPDATE"),
            Arguments.of("select all * from account", "select all * from account FOR UPDATE"),
            Arguments.of("select account.* from account", "select account.* from account FOR UPDATE"),
            Arguments.of("select *, balance from bank.public.account",
                    "select *, balance from bank.public.account FOR UPDATE"),
            // Parameterized predicates
            Arguments.of("select * from account where id = ?", "select * from account where id = ? FOR UPDATE"),
            Arguments.of("select * from account where id = $1 and region = $2",
                    "select * from account where id = $1 and region = $2 FOR UPDATE"),
            Arguments.of("select * from account where id in (?, ?, ?)",
                    "select * from account where id in (?, ?, ?) FOR UPDATE"),
            Arguments.of("select * from account where id not in (1, 2)",
                    "select * from account where id not in (1, 2) FOR UPDATE"),
            Arguments.of("select * from account where balance between ? and ?",
                    "select * from account where balance between ? and ? FOR UPDATE"),
            Arguments.of("select * from account where name like ? or name not ilike 'a%'",
                    "select * from account where name like ? or name not ilike 'a%' FOR UPDATE"),
            Arguments.of("select * from account where closed is not null and active is true",
                    "select * from account where closed is not null and active is true FOR UPDATE"),
            Arguments.of("select * from account where id = ?::uuid and amount >= -1.5e2",
                    "select * from account where id = ?::uuid and amount >= -1.5e2 FOR UPDATE"),
            Arguments.of("select * from account where not closed and balance + ? > 0",
                    "select * from account where not closed and balance + ? > 0 FOR UPDATE"),
            // Multiple columns and aliases
            Arguments.of("select id, balance, name from account",
                    "select id, balance, name from account FOR UPDATE"),
            Arguments.of("select a.id as account_id, a.balance bal from account a where a.id = ?",
                    "select a.id as account_id, a.balance bal from account a where a.id = ? FOR UPDATE"),
            Arguments.of("select a.id from account as a", "select a.id from account as a FOR UPDATE"),
            Arguments.of("select balance * 2 as doubled, -balance from account",
                    "select balance * 2 as doubled, -balance from account FOR UPDATE"),
            Arguments.of("select \"Order\", \"select\" from \"From\" where \"group\" = ?",
                    "select \"Order\", \"select\" from \"From\" where \"group\" = ? FOR UPDATE"),
            // Ordering and pagination
            Arguments.of("select * from account order by id", "select * from account order by id FOR UPDATE"),
            Arguments.of("select * from account order by balance desc nulls last, id asc",
                    "select * from account order by balance desc nulls last, id asc FOR UPDATE"),
            Arguments.of("select * from account where id > ? order by id limit 10",
                    "select * from account where id > ? order by id limit 10 FOR UPDATE"),
            Arguments.of("select * from account limit ? offset ?",
                    "select * from account limit ? offset ? FOR UPDATE"),
            Arguments.of("select * from account offset 5 rows limit all",
                    "select * from account offset 5 rows limit all FOR UPDATE"),
            // Parenthesized predicates
            Arguments.of("select * from account where (id = ? or id = ?) and (balance > 0)",
                    "select * from account where (id = ? or id = ?) and (balance > 0) FOR UPDATE"),
            Arguments.of("select * from account where ((a = 1) or (b = 2 and (c = 3)))",
                    "select * from account where ((a = 1) or (b = 2 and (c = 3))) FOR UPDATE"),
            // Comments and literals
            Arguments.of("select * from account -- trailing", "select * from account FOR UPDATE -- trailing"),
            Arguments.of("select * from account /* trailing */", "select * from account FOR UPDATE /* trailing */"),
            Arguments.of("/* leading */ select * from account", "/* leading */ select * from account FOR UPDATE"),
            Arguments.of("select * /* inline */ from account where name = 'x'",
                    "select * /* inline */ from account where name = 'x' FOR UPDATE"),
            Arguments.of("select * from account\n-- note\n", "select * from account FOR UPDATE\n-- note\n"),
            Arguments.of("select * from account  ", "select * from account FOR UPDATE  "),
            Arguments.of("select * from account; -- done", "select * from account FOR UPDATE; -- done")
    );

    @ParameterizedTest
    @VariableSource("eligibleQueries")
    public void whenQueryIsEligible_expectRewrite(String query, String expected) throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Assertions.assertEquals(expected, new SelectForUpdateProcessor().processQuery(connectionMock, query));

        Mockito.when(connectionMock.isReadOnly()).thenReturn(true);
        Assertions.assertEquals(query, new SelectForUpdateProcessor().processQuery(connectionMock, query));
    }

    // Ineligible queries: passed through unchanged
    public static final Stream<Arguments> ineligibleQueries = Stream.of(
            // Aggregates, including names never on any blacklist
            Arguments.of("select count(*) from account"),
            Arguments.of("select count (*) from account"),
            Arguments.of("select sum(balance) from account"),
            Arguments.of("select bit_xor_agg(flags) from account"),
            Arguments.of("select my_custom_agg(balance) from account"),
            Arguments.of("select \"count\"(*) from account"),
            Arguments.of("select * from account where balance > (select avg(balance) from account)"),
            // Any other function call
            Arguments.of("select lower(name) from account"),
            Arguments.of("select * from account where id = gen_random_uuid()"),
            Arguments.of("select * from account where name = lower(?)"),
            Arguments.of("select * from account where id = any(?)"),
            // Grouping
            Arguments.of("select region from account group by region"),
            Arguments.of("select region from account having region = 'x'"),
            // DISTINCT
            Arguments.of("select distinct region from account"),
            Arguments.of("select distinct on (region) * from account"),
            Arguments.of("select * from account where a is distinct from b"),
            // Window functions
            Arguments.of("select id, row_number() over (order by id) from account"),
            Arguments.of("select id from account window w as (order by id)"),
            // Set operations
            Arguments.of("select id from a union select id from b"),
            Arguments.of("select id from a union all select id from b"),
            Arguments.of("select id from a intersect select id from b"),
            Arguments.of("select id from a except select id from b"),
            // CTEs and nested queries
            Arguments.of("with x as (select * from account) select * from x"),
            Arguments.of("select * from (select * from account) as x"),
            Arguments.of("select * from account where id in (select id from other)"),
            Arguments.of("select * from account where exists (select 1 from other)"),
            Arguments.of("(select * from account)"),
            // Joins and multiple sources
            Arguments.of("select * from a join b on a.id = b.id"),
            Arguments.of("select * from a inner join b using (id)"),
            Arguments.of("select * from a left join b on a.id = b.id"),
            Arguments.of("select * from a natural join b"),
            Arguments.of("select * from a cross join b"),
            Arguments.of("select * from a, b where a.id = b.id"),
            Arguments.of("select * from a x, b y"),
            // Existing locking clauses
            Arguments.of("select * from account for update"),
            Arguments.of("select * from account for update nowait"),
            Arguments.of("select * from account for update skip locked"),
            Arguments.of("select * from account for share"),
            Arguments.of("select * from account for no key update"),
            Arguments.of("select * from account for key share"),
            Arguments.of("select * from account where id = ? for update of account"),
            // Historical reads
            Arguments.of("select * from account as of system time '-1s'"),
            Arguments.of("select * from account as of system time follower_read_timestamp() where id = 1"),
            Arguments.of("select * from account a as of system time '-1s'"),
            // System catalogs and virtual tables
            Arguments.of("select * from crdb_internal.ranges"),
            Arguments.of("select * from information_schema.tables"),
            Arguments.of("select * from pg_catalog.pg_class"),
            Arguments.of("select * from pg_extension.geometry_columns"),
            Arguments.of("select * from defaultdb.crdb_internal.ranges"),
            Arguments.of("select * from \"crdb_internal\".ranges"),
            Arguments.of("select * from system.users"),
            Arguments.of("select * from system.public.users"),
            Arguments.of("select * from pg_class"),
            Arguments.of("select * from PG_TABLES"),
            // Multiple statements
            Arguments.of("select * from a; select * from b"),
            Arguments.of("select * from a; delete from b"),
            Arguments.of("select * from a;; select 1"),
            // Missing FROM or not a plain SELECT
            Arguments.of("select 1"),
            Arguments.of("select now()"),
            Arguments.of("selection from account"),
            Arguments.of("values (1)"),
            Arguments.of("table account"),
            Arguments.of("explain select * from account"),
            Arguments.of("show tables"),
            // Malformed or unsupported syntax
            Arguments.of("select"),
            Arguments.of("select * from"),
            Arguments.of("select * from account where"),
            Arguments.of("select * from account where id ="),
            Arguments.of("select * from account where (id = 1"),
            Arguments.of("select * from account where id = 1)"),
            Arguments.of("select * from account order"),
            Arguments.of("select * from account limit"),
            Arguments.of("select * from account limit 1 limit 2"),
            Arguments.of("select * from account where id = 1 2"),
            Arguments.of("select a b c from account"),
            Arguments.of("select * from account@primary"),
            Arguments.of("select * from account@{FORCE_INDEX=primary}"),
            Arguments.of("select * from account where tags @> ?"),
            Arguments.of("select * from account where data->>'k' = ?"),
            Arguments.of("select * from account where arr[1] = ?"),
            Arguments.of("select * from account where id = :id"),
            Arguments.of("select * from account where x ?? 'k'"),
            Arguments.of("select * from account where x = 0x1F"),
            Arguments.of("select * from account where x = b'abc'"),
            Arguments.of("select * from account where x = case when a then 1 else 2 end"),
            Arguments.of("select * from account where x = cast(? as int)"),
            Arguments.of("select * from account where x::decimal(10,2) > 0"),
            Arguments.of("select * from account where (a, b) = (?, ?)"),
            Arguments.of("select * from account fetch first 10 rows only"),
            Arguments.of("select * from account a.b"),
            Arguments.of("select a.b.c.d from account"),
            Arguments.of("select * from a.b.c.d")
    );

    @ParameterizedTest
    @VariableSource("ineligibleQueries")
    public void whenQueryIsIneligible_expectPassThrough(String query) throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Assertions.assertEquals(query, new SelectForUpdateProcessor().processQuery(connectionMock, query));
        Assertions.assertEquals(-1, SelectForUpdateProcessor.qualify(query));
    }

    // Lexical edge cases: [query, expected]
    public static final Stream<Arguments> lexicalQueries = Stream.of(
            // Keywords inside string literals don't disqualify
            Arguments.of("select * from t where s = 'select count(*) from x group by y union for update'",
                    "select * from t where s = 'select count(*) from x group by y union for update' FOR UPDATE"),
            Arguments.of("select * from t where s = 'as of system time'",
                    "select * from t where s = 'as of system time' FOR UPDATE"),
            // Keywords inside quoted identifiers
            Arguments.of("select \"group by\", \"for update\" from \"union\"",
                    "select \"group by\", \"for update\" from \"union\" FOR UPDATE"),
            Arguments.of("select \"a\"\"b\" from t", "select \"a\"\"b\" from t FOR UPDATE"),
            // Escaped quotes
            Arguments.of("select * from t where s = 'it''s; fine'", "select * from t where s = 'it''s; fine' FOR UPDATE"),
            Arguments.of("select * from t where s = E'it\\'s; fine'",
                    "select * from t where s = E'it\\'s; fine' FOR UPDATE"),
            Arguments.of("select * from t where s = 'back\\'", "select * from t where s = 'back\\' FOR UPDATE"),
            // Dollar-quoted strings
            Arguments.of("select * from t where s = $$ group by ; ' $$",
                    "select * from t where s = $$ group by ; ' $$ FOR UPDATE"),
            // Tagged dollar quotes are not supported (fail closed)
            Arguments.of("select * from t where s = $tag$ $$ union $tag$",
                    "select * from t where s = $tag$ $$ union $tag$"),
            Arguments.of("select * from t where s = $tag$ x $tag$", "select * from t where s = $tag$ x $tag$"),
            // Supplementary characters: offsets are code points in ANTLR, chars in Java
            Arguments.of("select * from t where s = '\uD83D\uDE00\uD83D\uDE00' -- \uD83D\uDE00",
                    "select * from t where s = '\uD83D\uDE00\uD83D\uDE00' FOR UPDATE -- \uD83D\uDE00"),
            Arguments.of("select * from \"t\uD83D\uDE00\";", "select * from \"t\uD83D\uDE00\" FOR UPDATE;"),
            Arguments.of("select \u00fc, \u00e5\u00e4 from \u00f6l", "select \u00fc, \u00e5\u00e4 from \u00f6l FOR UPDATE"),
            // Nested comments
            Arguments.of("select * from t /* outer /* inner */ still comment group by */ where id = 1",
                    "select * from t /* outer /* inner */ still comment group by */ where id = 1 FOR UPDATE"),
            // Comment boundaries
            Arguments.of("select * from t /**/", "select * from t FOR UPDATE /**/"),
            Arguments.of("select * from t /* a */ /* b */ where id = 1",
                    "select * from t /* a */ /* b */ where id = 1 FOR UPDATE"),
            Arguments.of("select * from t /* /* a */ */ /* b */", "select * from t FOR UPDATE /* /* a */ */ /* b */"),
            Arguments.of("select * from t /* a */ */", "select * from t /* a */ */"),
            Arguments.of("select * from t /*/ where id = 1 */", "select * from t FOR UPDATE /*/ where id = 1 */"),
            // Trailing comments
            Arguments.of("select * from t where id = 1 -- for update", "select * from t where id = 1 FOR UPDATE -- for update"),
            Arguments.of("select * from t where id = 1 /* ; */", "select * from t where id = 1 FOR UPDATE /* ; */"),
            Arguments.of("select * from t; /* c */ ;", "select * from t FOR UPDATE; /* c */ ;"),
            // Semicolons inside literals and comments
            Arguments.of("select * from t where s = ';'", "select * from t where s = ';' FOR UPDATE"),
            Arguments.of("select * from t where s = ';';", "select * from t where s = ';' FOR UPDATE;"),
            Arguments.of("select * from t -- ; select * from u\nwhere id = 1",
                    "select * from t -- ; select * from u\nwhere id = 1 FOR UPDATE"),
            // Unclosed literals and comments
            Arguments.of("select * from t where s = 'unclosed", "select * from t where s = 'unclosed"),
            Arguments.of("select * from t where s = E'unclosed\\'", "select * from t where s = E'unclosed\\'"),
            Arguments.of("select * from \"unclosed", "select * from \"unclosed"),
            Arguments.of("select * from t where s = $$unclosed", "select * from t where s = $$unclosed"),
            Arguments.of("select * from t /* unclosed", "select * from t /* unclosed"),
            Arguments.of("select * from t /* outer /* inner */ unclosed", "select * from t /* outer /* inner */ unclosed"),
            // Disqualifying keywords hidden from naive scanning must still disqualify
            Arguments.of("select * from t/**/group by x", "select * from t/**/group by x"),
            Arguments.of("select * from t where s = 'x' union select * from u",
                    "select * from t where s = 'x' union select * from u")
    );

    @ParameterizedTest
    @VariableSource("lexicalQueries")
    public void whenProcessingLexicalEdgeCases_expectCorrectQualification(String query, String expected)
            throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        Assertions.assertEquals(expected, new SelectForUpdateProcessor().processQuery(connectionMock, query));
    }

    private static List<? extends Token> lex(String sql) {
        CockroachSelectLexer lexer = new CockroachSelectLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        return lexer.getAllTokens();
    }

    private static boolean hasUnsupportedToken(String sql) {
        return lex(sql).stream().anyMatch(t -> t.getType() == CockroachSelectLexer.UNSUPPORTED
                || t.getType() == CockroachSelectLexer.UNSUPPORTED_NUMBER);
    }

    @Test
    public void whenLexing_expectOriginalOffsetsAndSkippedComments() {
        String sql = "select /* c /* nested */ */ \"a b\", 'x''y' from t -- tail";
        Assertions.assertEquals(Token.HIDDEN_CHANNEL, lex(sql).get(1).getChannel());
        Assertions.assertEquals("/* c /* nested */ */", lex(sql).get(1).getText());

        List<? extends Token> tokens = lex(sql).stream()
                .filter(t -> t.getChannel() == Token.DEFAULT_CHANNEL).toList();
        Assertions.assertEquals(List.of("select", "\"a b\"", ",", "'x''y'", "from", "t"),
                tokens.stream().map(Token::getText).toList());
        for (Token token : tokens) {
            Assertions.assertEquals(token.getText(), sql.substring(token.getStartIndex(), token.getStopIndex() + 1));
        }
        Assertions.assertEquals(CockroachSelectLexer.QUOTED_IDENTIFIER, tokens.get(1).getType());
        Assertions.assertEquals(CockroachSelectLexer.STRING, tokens.get(3).getType());
        Assertions.assertEquals(CockroachSelectLexer.RESERVED, lex("group").get(0).getType());
        Assertions.assertEquals(CockroachSelectLexer.IDENTIFIER, lex("grouping").get(0).getType());
    }

    @Test
    public void whenLexingUnsupportedOrUnclosed_expectNoQualification() {
        // Unsupported tokens
        Assertions.assertTrue(hasUnsupportedToken("select 'x"));
        Assertions.assertTrue(hasUnsupportedToken("select \"x"));
        Assertions.assertTrue(hasUnsupportedToken("select $a$ x"));
        Assertions.assertTrue(hasUnsupportedToken("select 1abc"));
        Assertions.assertTrue(hasUnsupportedToken("select 0x1F"));
        Assertions.assertTrue(hasUnsupportedToken("select a # b"));
        Assertions.assertFalse(hasUnsupportedToken("select $1, ?, 1.5e-3, .5, 1e5 from t"));

        // Lexes, but never parses
        for (String sql : List.of("select * from t /* x", "select * from t where s = x'00'", "select * from t where s = b'00'")) {
            Assertions.assertEquals(-1, SelectForUpdateProcessor.qualify(sql), sql);
        }
    }

    @Test
    public void whenQualifying_expectInsertionOffset() {
        Assertions.assertEquals(15, SelectForUpdateProcessor.qualify("select * from t;"));
        Assertions.assertEquals(15, SelectForUpdateProcessor.qualify("select * from t  -- c"));
        Assertions.assertEquals(-1, SelectForUpdateProcessor.qualify("select * from t group by a"));
    }

    @Test
    public void whenDeeplyNested_expectBoundedRecursion() throws SQLException {
        Connection connectionMock = Mockito.mock(Connection.class);
        String shallow = "select * from t where " + "(".repeat(32) + "a = 1" + ")".repeat(32);
        Assertions.assertEquals(shallow + " FOR UPDATE",
                new SelectForUpdateProcessor().processQuery(connectionMock, shallow));

        String deep = "select * from t where " + "(".repeat(100_000) + "a = 1" + ")".repeat(100_000);
        Assertions.assertEquals(deep, new SelectForUpdateProcessor().processQuery(connectionMock, deep));
    }
}
