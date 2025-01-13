package io.cockroachdb.jdbc.rewrite;

import io.cockroachdb.jdbc.parser.SQLParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.stream.IntStream;

@Disabled
public class SQLParserPerformanceTest {
    public static final List<Arguments> inserts = List.of(
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

    public static final List<Arguments> upserts = List.of(
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

    public static final List<Arguments> updates = List.of(
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

    @Test
    public void givenInsertStatement_expectRewriteIfValid() {
        IntStream.rangeClosed(1, 50_000).forEach(value -> {
            inserts.forEach(arguments -> {
                boolean valid = (boolean) arguments.get()[0];
                String before = (String) arguments.get()[1];
                Assertions.assertEquals(valid, BatchRewriteProcessor.isQualifiedInsertStatement(before), before);
                if (valid) {
                    String after = BatchRewriteProcessor.rewriteInsertStatement(before);
//                System.out.println(after);
                    Assertions.assertNotEquals(before, after);
                } else {
                    Assertions.assertThrowsExactly(SQLParseException.class, () -> {
                        BatchRewriteProcessor.rewriteInsertStatement(before);
                    });
                }
            });
        });
    }

    @Test
    public void givenUpsertStatement_expectRewriteIfValid() {
        IntStream.rangeClosed(1, 50_000).forEach(value -> {
            upserts.forEach(arguments -> {
                boolean valid = (boolean) arguments.get()[0];
                String before = (String) arguments.get()[1];
                Assertions.assertEquals(valid, BatchRewriteProcessor.isQualifiedUpsertStatement(before), before);
                if (valid) {
                    String after = BatchRewriteProcessor.rewriteUpsertStatement(before);
//                System.out.println(after);
                    Assertions.assertNotEquals(before, after);
                } else {
                    Assertions.assertThrowsExactly(SQLParseException.class, () -> {
                        BatchRewriteProcessor.rewriteUpsertStatement(before);
                    });
                }
            });
        });
    }

    @Test
    public void givenUpdateStatement_expectRewriteIfValid() {
        IntStream.rangeClosed(1, 50_000).forEach(value -> {
            updates.forEach(arguments -> {
                boolean valid = (boolean) arguments.get()[0];
                String before = (String) arguments.get()[1];
                Assertions.assertEquals(valid, BatchRewriteProcessor.isQualifiedUpdateStatement(before), before);
                if (valid) {
                    String after = BatchRewriteProcessor.rewriteUpdateStatement(before);
//                System.out.println(after);
                    Assertions.assertNotEquals(before, after);
                } else {
                    Assertions.assertThrowsExactly(SQLParseException.class, () -> {
                        BatchRewriteProcessor.rewriteUpdateStatement(before);
                    });
                }
            });
        });
    }
}

