package io.cockroachdb.jdbc.rewrite;

import io.cockroachdb.jdbc.parser.CockroachSQLLexer;
import io.cockroachdb.jdbc.parser.CockroachSQLParser;
import io.cockroachdb.jdbc.parser.FailFastErrorListener;
import io.cockroachdb.jdbc.parser.FailFastErrorStrategy;
import io.cockroachdb.jdbc.parser.SQLParseException;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;

/**
 * Factory class for Cockroach SQL batch DML statement rewrites.
 *
 * @author Kai Niemi
 */
public abstract class BatchRewriteProcessor {
    private BatchRewriteProcessor() {
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
        // Pre-parse to look for complex expressions / predicates and placeholders
        try {
            CockroachSQLParser parser = createParser(query);
            parser.addParseListener(new BatchInsertRewriteProcessor(sql -> {
            }));
            parser.addParseListener(new BatchUpsertRewriteProcessor(sql -> {
            }));
            parser.addParseListener(new BatchUpdateRewriteProcessor(sql -> {
            }));
            parser.root();
            return true;
        } catch (SQLParseException e) {
            return false;
        }
    }

    public static String rewriteInsertStatement(String query) {
        StringBuilder after = new StringBuilder();

        CockroachSQLParser parser = createParser(query);
        parser.addParseListener(new BatchInsertRewriteProcessor(after::append));
        parser.insertStatement();

        return after.toString();
    }

    public static String rewriteUpsertStatement(String query) {
        StringBuilder after = new StringBuilder();

        CockroachSQLParser parser = createParser(query);
        parser.addParseListener(new BatchUpsertRewriteProcessor(after::append));
        parser.upsertStatement();

        return after.toString();
    }

    public static String rewriteUpdateStatement(String query) {
        StringBuilder after = new StringBuilder();

        CockroachSQLParser parser = createParser(query);
        parser.addParseListener(new BatchUpdateRewriteProcessor(after::append));
        parser.updateStatement();

        return after.toString();
    }

    private static CockroachSQLParser createParser(String expression) {
        final ANTLRErrorListener errorListener = new FailFastErrorListener();

        CockroachSQLLexer lexer = new CockroachSQLLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CockroachSQLParser parser = new CockroachSQLParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        parser.setErrorHandler(new FailFastErrorStrategy());

        // Grammar is simple enough with low ambiguity level
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);

        return parser;
    }
}
