package io.cockroachdb.jdbc.rewrite;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

public abstract class AbstractCockroachSQLListener extends CockroachSQLBaseListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Deque<Object> stack = new ArrayDeque<>();

    protected Logger getLogger() {
        return logger;
    }

    /**
     * Push current token to the top of stack.
     *
     * @param o   object to push
     * @param ctx current rule context
     */
    protected void push(Object o, ParserRuleContext ctx) {
        stack.push(o);

        if (logger.isTraceEnabled()) {
            logger.trace("{}: push [{}] of type [{}] to stack: {}",
                    ctx.getRuleContext().getClass().getSimpleName(),
                    o, o.getClass().getSimpleName(), stack);
        }
    }

    /**
     * Pop the top of the stack.
     *
     * @param type expected type
     * @param ctx  current rule context
     * @param <T>  generic type
     * @return top of stack
     */
    protected <T> T pop(Class<T> type, ParserRuleContext ctx) {
        Object top = this.stack.pop();

        try {
            if (logger.isTraceEnabled()) {
                logger.trace("{}: pop: [{}] of type [{}] from stack: {}",
                        ctx.getRuleContext().getClass().getSimpleName(),
                        top, top.getClass().getSimpleName(), stack);
            }
            return type.cast(top);
        } catch (ClassCastException e) {
            throw SQLParseException.from("Cannot cast '" + top + "' of type "
                    + top.getClass().getSimpleName() + " into " + type.getSimpleName());
        }
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        if (logger.isTraceEnabled()) {
            String prefix = ctx.getRuleContext().getClass().getSimpleName();
            logger.trace("prefix [{}] text [{}]", prefix, ctx.getText());
            for (int i = 0; i < ctx.getChildCount(); i++) {
                logger.trace("\t[{}] {}", i, ctx.getChild(i).getText());
            }
        }
    }
}
