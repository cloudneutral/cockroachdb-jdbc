package io.cockroachdb.jdbc.rewrite;

import io.cockroachdb.jdbc.rewrite.batch.InsertRewriteParseTreeListener;
import io.cockroachdb.jdbc.rewrite.batch.UpdateRewriteParseTreeListener;
import io.cockroachdb.jdbc.rewrite.batch.UpsertRewriteParseTreeListener;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public abstract class CockroachParserFactory {
    private CockroachParserFactory() {
    }

    public static boolean isQualifiedInsertStatement(String query) {
        return query.toLowerCase().startsWith("insert ") && isQualifiedStatement(query);
    }

    public static boolean isQualifiedUpsertStatement(String query) {
        return query.toLowerCase().startsWith("upsert ") && isQualifiedStatement(query);
    }

    public static boolean isQualifiedUpdateStatement(String query) {
        return query.toLowerCase().startsWith("update ") && isQualifiedStatement(query);
    }

    public static boolean isQualifiedStatement(String query) {
        // Check for any complex expressions / predicates and placeholders
        try {
            CockroachParser parser = createParser(query);
            parser.addParseListener(new InsertRewriteParseTreeListener(sql -> {
            }));
            parser.addParseListener(new UpsertRewriteParseTreeListener(sql -> {
            }));
            parser.addParseListener(new UpdateRewriteParseTreeListener(sql -> {
            }));
            parser.root();
            return true;
        } catch (SQLParseException e) {
            return false;
        }
    }

    public static String rewriteInsertStatement(String query) {
        StringBuilder after = new StringBuilder();

        CockroachParser parser = createParser(query);
        parser.addParseListener(new InsertRewriteParseTreeListener(after::append));
        parser.insertStatement();

        return after.toString();
    }

    public static String rewriteUpsertStatement(String query) {
        StringBuilder after = new StringBuilder();

        CockroachParser parser = createParser(query);
        parser.addParseListener(new UpsertRewriteParseTreeListener(after::append));
        parser.upsertStatement();

        return after.toString();
    }

    public static String rewriteUpdateStatement(String query) {
        StringBuilder after = new StringBuilder();

        CockroachParser parser = createParser(query);
        parser.addParseListener(new UpdateRewriteParseTreeListener(after::append));
        parser.updateStatement();

        return after.toString();
    }

    private static CockroachParser createParser(String expression) {
        final ANTLRErrorListener errorListener = new FailFastErrorListener();

        CockroachLexer lexer = new CockroachLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CockroachParser parser = new CockroachParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        parser.setErrorHandler(new FailFastErrorStrategy());

        return parser;
    }
}
