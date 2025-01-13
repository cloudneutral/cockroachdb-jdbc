package io.cockroachdb.jdbc.parser;

import org.antlr.v4.runtime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR4 error strategy that propagates all parse errors without
 * any recovery attempts.
 *
 * @author Kai Niemi
 */
public class FailFastErrorStrategy extends DefaultErrorStrategy {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Make sure we don't attempt to recover from problems in subrules.
     */
    @Override
    public void sync(Parser recognizer) {

    }

    @Override
    public void recover(Parser recognizer, RecognitionException e) {
        for (ParserRuleContext context = recognizer.getContext(); context != null; context = context.getParent()) {
            context.exception = e;
        }
        throw SQLParseException.from(e.toString(), recognizer);
    }

    @Override
    public Token recoverInline(Parser recognizer) throws RecognitionException {
        InputMismatchException e = new InputMismatchException(recognizer);
        for (ParserRuleContext context = recognizer.getContext(); context != null; context = context.getParent()) {
            context.exception = e;
        }

        String msg = "Mismatched input " + getTokenErrorDisplay(e.getOffendingToken())
                + ". Expecting one of: " + e.getExpectedTokens().toString(recognizer.getVocabulary());

        throw SQLParseException.from(msg, recognizer);
    }

    @Override
    public void reportError(Parser recognizer, RecognitionException e) {
        if (!inErrorRecoveryMode(recognizer)) {
            if (e instanceof NoViableAltException) {
                reportNoViableAlternative(recognizer, (NoViableAltException) e);
            } else if (e instanceof InputMismatchException) {
                reportInputMismatch(recognizer, (InputMismatchException) e);
            } else if (e instanceof FailedPredicateException) {
                reportFailedPredicate(recognizer, (FailedPredicateException) e);
            } else {
                recognizer.removeParseListeners();
                recognizer.notifyErrorListeners(e.getOffendingToken(), e.getMessage(), e);
            }
        }
    }

    @Override
    protected void reportNoViableAlternative(Parser recognizer, NoViableAltException cause) {
        String msg = "No viable alternative input for "
                + getTokenErrorDisplay(cause.getOffendingToken())
                + ". Expecting one of: " + cause.getExpectedTokens().toString(recognizer.getVocabulary());
        throw SQLParseException.from(msg, recognizer);
    }

    @Override
    protected void reportInputMismatch(Parser recognizer, InputMismatchException cause) {
        String msg = "Mismatched input " + getTokenErrorDisplay(cause.getOffendingToken())
                + ". Expecting one of: " + cause.getExpectedTokens().toString(recognizer.getVocabulary());
        throw SQLParseException.from(msg, recognizer);
    }

    @Override
    public void reportMissingToken(Parser recognizer) {
        String msg = "Missing " + getExpectedTokens(recognizer).toString(recognizer.getVocabulary())
                + " at " + getTokenErrorDisplay(recognizer.getCurrentToken());
        throw SQLParseException.from(msg, recognizer);
    }

    @Override
    protected void reportUnwantedToken(Parser recognizer) {
        String msg = "Unwanted token " + getTokenErrorDisplay(recognizer.getCurrentToken())
                + " expected " + getExpectedTokens(recognizer).toString(recognizer.getVocabulary());
        throw SQLParseException.from(msg, recognizer);
    }
}
