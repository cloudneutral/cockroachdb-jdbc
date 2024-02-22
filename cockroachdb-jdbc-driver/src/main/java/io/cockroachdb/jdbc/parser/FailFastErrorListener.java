package io.cockroachdb.jdbc.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Provides a fail fast Antlr4 error listener.
 *
 * @author Kai Niemi
 */
public class FailFastErrorListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                            int charPositionInLine,
                            String msg, RecognitionException e) {
        String errorMsg = String.format("line %s:%s at %s: %s", line, charPositionInLine, offendingSymbol, msg);
        throw SQLParseException.from(errorMsg);
    }
}
