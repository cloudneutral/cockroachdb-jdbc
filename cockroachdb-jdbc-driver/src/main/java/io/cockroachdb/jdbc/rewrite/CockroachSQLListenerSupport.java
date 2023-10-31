package io.cockroachdb.jdbc.rewrite;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

public abstract class CockroachSQLListenerSupport extends CockroachSQLBaseListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Deque<Object> stack = new ArrayDeque<>();

    protected Logger getLogger() {
        return logger;
    }

    protected void push(Object o) {
        stack.push(o);
        logger.trace("push: {} [{}] after: {}", o, o.getClass().getSimpleName(), stack);
    }

    protected <T> T pop(Class<T> type) {
        Object top = this.stack.pop();
        try {
            logger.trace("pop: {} [{}] after: {}", top, top.getClass().getSimpleName(), stack);
            return type.cast(top);
        } catch (ClassCastException e) {
            throw SQLParseException.from("Cannot cast '" + top + "' of type "
                    + top.getClass().getSimpleName() + " into " + type.getSimpleName());
        }
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        logRuleContext(ctx.getRuleContext().getClass().getSimpleName(), ctx);
    }

    protected void logRuleContext(String prefix, ParserRuleContext ctx) {
        if (logger.isTraceEnabled()) {
            logger.trace("prefix [{}] text [{}]", prefix, ctx.getText());
            for (int i = 0; i < ctx.getChildCount(); i++) {
                logger.trace("\t[{}] {}", i, ctx.getChild(i).getText());
            }
        }
    }
}
