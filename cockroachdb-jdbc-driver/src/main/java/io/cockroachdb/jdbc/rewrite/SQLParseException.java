package io.cockroachdb.jdbc.rewrite;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Exception thrown when an expression violates the grammar or cannot
 * be compiled.
 */
public class SQLParseException extends RuntimeException {
    public static SQLParseException from(String message, Parser parser) {
        parser.removeParseListeners();
        Token token = parser.getCurrentToken();
        String line = token.getInputStream().getText(Interval.of(0, token.getStopIndex()));
        return new SQLParseException(message +
                ". Near token '" +
                token.getText() +
                "' at position " +
                token.getCharPositionInLine() +
                ":\n" + line);
    }

    public static SQLParseException from(String message) {
        return new SQLParseException(message);
    }

    public SQLParseException(String message) {
        super(message);
    }
}
