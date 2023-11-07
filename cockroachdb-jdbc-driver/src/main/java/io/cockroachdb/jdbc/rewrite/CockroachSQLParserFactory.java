package io.cockroachdb.jdbc.rewrite;

import io.cockroachdb.jdbc.rewrite.update.RewriteUpdateParseTreeListener;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public abstract class CockroachSQLParserFactory {
    private CockroachSQLParserFactory() {
    }

    public static String rewriteUpdateQuery(String query) {
        StringBuilder after = new StringBuilder();

        CockroachSQLParser parser = createParser(query);
        parser.addParseListener(new RewriteUpdateParseTreeListener(after::append));
        parser.updateStatement();

        return after.toString();
    }

    private static CockroachSQLParser createParser(String expression) {
        final ANTLRErrorListener errorListener = new FailFastErrorListener();
        final ANTLRErrorStrategy errorStrategy = new FailFastErrorStrategy();

        CockroachSQLLexer lexer = new CockroachSQLLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CockroachSQLParser parser = new CockroachSQLParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        parser.setErrorHandler(errorStrategy);

        return parser;
    }
}
