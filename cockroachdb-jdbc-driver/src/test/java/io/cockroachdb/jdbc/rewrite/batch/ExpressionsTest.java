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
public class ExpressionsTest {
    public static final Stream<Arguments> inserts = Stream.of(
            Arguments.of(true, "insert into t (a,b) values (?,?)"),
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

    public static final Stream<Arguments> updates = Stream.of(
            Arguments.of(true, "update t set a=?, b=? where 1=2"),

            Arguments.of(false, "select * from pg_extension.x where id in (?)"),
            Arguments.of(false, "delete from t where 1=2")
    );

    @ParameterizedTest
    @VariableSource("inserts")
    public void givenInsertStatement_expectRewriteIfValid(boolean valid, String before) {
        Assertions.assertEquals(valid, CockroachParserFactory.isQualifiedInsertStatement(before));
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
    @VariableSource("updates")
    public void givenUpdateStatement_expectRewriteIfValid(boolean valid, String before) {
        Assertions.assertEquals(valid, CockroachParserFactory.isQualifiedUpdateStatement(before));
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
