package io.cockroachdb.jdbc.rewrite.batch;

import io.cockroachdb.jdbc.VariableSource;
import io.cockroachdb.jdbc.rewrite.CockroachParserFactory;
import io.cockroachdb.jdbc.rewrite.SQLParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

@Tag("unit-test")
public class SQLExpressionsTest {
    public static final Stream<Arguments> inserts = Stream.of(
            Arguments.of(true, "insert into t (a,b) values (?,?)"),
            Arguments.of(true, "insert into t (a,b) values (?::int,?::bool)"),
            Arguments.of(true, "insert into t (a,b,c) values (?,?,foo())"),
            Arguments.of(true, "insert into t (a,b,c) values (?,?,123)"),
            Arguments.of(true, "insert into t (a,b,c) values (?,?,'abc')"),
            Arguments.of(true, "insert into t (a,b,c) values (?,?,foo()) on conflict (a) do nothing"),
            Arguments.of(true, "insert into t (a,b,c) values (?,?,foo()) on conflict on constraint x do nothing"),

            Arguments.of(false, "insert into t (a,b,c) values (?,?,123+45)"),
            Arguments.of(false, "insert into t (a,b,c) values (?,?,(select (1)))"),
            Arguments.of(false, "insert into t values (?,?)"),
            Arguments.of(false, "insert into t values"),
            Arguments.of(false, "insert into t"),

            Arguments.of(false, "select * from pg_extension.x where id in (?)"),
            Arguments.of(false, "update t set a=?, b=? where 1=2"),
            Arguments.of(false, "delete from t where 1=2")
    );

    public static final Stream<Arguments> upserts = Stream.of(
            Arguments.of(true, "upsert into t (a,b) values (?,?)"),
            Arguments.of(true, "upsert into t (a,b) values (?::int,?::bool)"),
            Arguments.of(true, "upsert into t (a,b,c) values (?,?,foo())"),
            Arguments.of(true, "upsert into t (a,b,c) values (?,?,123)"),
            Arguments.of(true, "upsert into t (a,b,c) values (?,?,'abc')"),

            Arguments.of(false, "upsert into t (a,b,c) values (?,?,123+45)"),
            Arguments.of(false, "upsert into t (a,b,c) values (?,?,(select (1)))"),
            Arguments.of(false, "upsert into t values (?,?)"),
            Arguments.of(false, "upsert into t values"),
            Arguments.of(false, "upsert into t"),

            Arguments.of(false, "insert into t (a,b) values (?,?)"),
            Arguments.of(false, "select * from pg_extension.x where id in (?)"),
            Arguments.of(false, "update t set a=?, b=? where 1=2"),
            Arguments.of(false, "delete from t where 1=2")
    );

    public static final Stream<Arguments> updates = Stream.of(
            Arguments.of(true, "update x.t set a=?, b=? where 1=2"),
            Arguments.of(true, "update x.t set a=?, b=? where false"),
            Arguments.of(true, "update t set a=?, b=? where 1=2"),
            Arguments.of(true, "update t set a=-?, b=+? where a > 1+2"),
            Arguments.of(true, "update t set a=-?, b=-? where a + 1 != 1+2"),
            Arguments.of(true, "update t set a=-?, b= foo(-? + (bar(x))) where a + 1 != 1+2"),

            Arguments.of(false, "insert into t (a,b,c) values (?,?,123)"),
            Arguments.of(false, "select * from pg_extension.x where id in (?)"),
            Arguments.of(false, "delete from t where 1=2")
    );

    @ParameterizedTest
    @VariableSource("inserts")
    public void givenInsertStatement_expectRewriteIfValid(boolean valid, String before) {
        Assertions.assertEquals(valid, CockroachParserFactory.isQualifiedInsertStatement(before), before);
        if (valid) {
            String after = CockroachParserFactory.rewriteInsertStatement(before);
            System.out.println(after);
            Assertions.assertNotEquals(before, after);
        } else {
            Assertions.assertThrowsExactly(SQLParseException.class, () -> {
                CockroachParserFactory.rewriteInsertStatement(before);
            });
        }
    }

    @ParameterizedTest
    @VariableSource("upserts")
    public void givenUpsertStatement_expectRewriteIfValid(boolean valid, String before) {
        Assertions.assertEquals(valid, CockroachParserFactory.isQualifiedUpsertStatement(before), before);
        if (valid) {
            String after = CockroachParserFactory.rewriteUpsertStatement(before);
            System.out.println(after);
            Assertions.assertNotEquals(before, after);
        } else {
            Assertions.assertThrowsExactly(SQLParseException.class, () -> {
                CockroachParserFactory.rewriteUpsertStatement(before);
            });
        }
    }

    @ParameterizedTest
    @VariableSource("updates")
    public void givenUpdateStatement_expectRewriteIfValid(boolean valid, String before) {
        Assertions.assertEquals(valid, CockroachParserFactory.isQualifiedUpdateStatement(before), before);
        if (valid) {
            String after = CockroachParserFactory.rewriteUpdateStatement(before);
            System.out.println(after);
            Assertions.assertNotEquals(before, after);
        } else {
            Assertions.assertThrowsExactly(SQLParseException.class, () -> {
                CockroachParserFactory.rewriteUpdateStatement(before);
            });
        }
    }
}
