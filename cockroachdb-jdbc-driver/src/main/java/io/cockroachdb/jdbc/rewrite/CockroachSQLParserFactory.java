package io.cockroachdb.jdbc.rewrite;

import io.cockroachdb.jdbc.rewrite.update.UpdateRewriteParseTreeListener;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public abstract class CockroachSQLParserFactory {
    private CockroachSQLParserFactory() {
    }

    public static boolean isInsertStatement(String query) {
        return query.toLowerCase().startsWith("update ");
    }

    public static boolean isUpsertStatement(String query) {
        return query.toLowerCase().startsWith("upsert ");
    }

    public static boolean isUpdateStatement(String query) {
        return query.toLowerCase().startsWith("update ");
    }

    public static boolean isQualifiedUpdateStatement(String query) {
        if (!isUpdateStatement(query)) {
            return false;
        }

        // Check for any complex expressions / predicates and placeholders
        try {
            UpdateRewriteParseTreeListener listener = new UpdateRewriteParseTreeListener(sql -> {});
            CockroachSQLParser parser = createParser(query);
            parser.addParseListener(listener);
            parser.root();
            return listener.hasPlaceholders();
        } catch (SQLParseException e) {
            return false;
        }
    }

    public static String rewriteUpdateStatement(String query) {
        StringBuilder after = new StringBuilder();

        CockroachSQLParser parser = createParser(query);
        parser.addParseListener(new UpdateRewriteParseTreeListener(after::append));
        parser.root();

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

        return parser;
    }
}
