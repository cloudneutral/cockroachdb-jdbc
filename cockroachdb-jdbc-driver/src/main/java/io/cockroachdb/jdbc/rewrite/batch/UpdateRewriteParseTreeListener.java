package io.cockroachdb.jdbc.rewrite.batch;

import io.cockroachdb.jdbc.rewrite.AbstractCockroachParserListener;
import io.cockroachdb.jdbc.rewrite.CockroachParser;
import io.cockroachdb.jdbc.rewrite.SQLParseException;
import io.cockroachdb.jdbc.util.Pair;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Parse tree listener for rewriting UPDATE statements to use batch arrays.
 */
public class UpdateRewriteParseTreeListener extends AbstractCockroachParserListener {
    private final List<Pair<String, String>> setClauseList = new ArrayList<>();

    private final Set<String> placeHolders = new TreeSet<>();

    private final AtomicInteger parameterIndex = new AtomicInteger();

    private String tableName;

    private String predicate;

    private final Consumer<String> consumer;

    private String fromQueryAlias = "_dt"; // short for datatable

    private String parameterPrefix = "p";

    public UpdateRewriteParseTreeListener(Consumer<String> consumer) {
        this.consumer = consumer;
    }

    public String getFromQueryAlias() {
        return fromQueryAlias;
    }

    public void setFromQueryAlias(String fromQueryAlias) {
        this.fromQueryAlias = fromQueryAlias;
    }

    public String getParameterPrefix() {
        return parameterPrefix;
    }

    public void setParameterPrefix(String parameterPrefix) {
        this.parameterPrefix = parameterPrefix;
    }

    @Override
    public void exitUpdateStatement(CockroachParser.UpdateStatementContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ")
                .append(tableName)
                .append(" SET ");

        int c = 0;
        for (Pair<String, String> pair : setClauseList) {
            if (c++ > 0) {
                sb.append(", ");
            }
            sb.append(pair.getFirst())
                    .append(" = ")
                    .append(pair.getSecond());
        }

        sb.append(" FROM (SELECT ");

        c = 0;
        for (String param : placeHolders) {
            if (c++ > 0) {
                sb.append(", ");
            }
            sb.append("UNNEST(?) AS ")
                    .append(param);
        }

        sb.append(") AS ")
                .append(fromQueryAlias)
                .append(" WHERE ")
                .append(predicate);

        consumer.accept(sb.toString());
    }

    @Override
    public void exitTableName(CockroachParser.TableNameContext ctx) {
        this.tableName = ctx.getText();
    }

    @Override
    public void exitSetClause(CockroachParser.SetClauseContext ctx) {
        String right = pop(String.class, ctx);
        setClauseList.add(Pair.of(ctx.identifier().getText(), right));
    }

    @Override
    public void exitWhereClause(CockroachParser.WhereClauseContext ctx) {
        this.predicate = pop(String.class, ctx);
    }

    @Override
    public void exitIsNullExpression(CockroachParser.IsNullExpressionContext ctx) {
        String prefix = pop(String.class, ctx);

        StringBuilder sb = new StringBuilder()
                .append(prefix);

        if (ctx.IS() != null) {
            sb.append(" IS");
        }
        if (ctx.NOT() != null) {
            sb.append(" NOT");
        }
        sb.append(" NULL");

        push(sb.toString(), ctx);
    }

    @Override
    public void exitLogicalExpression(CockroachParser.LogicalExpressionContext ctx) {
        String right = pop(String.class, ctx);
        String left = pop(String.class, ctx);

        if (ctx.logicalOperator().AND() != null) {
            push(left + " AND " + right, ctx);
        } else if (ctx.logicalOperator().OR() != null) {
            push(left + " OR " + right, ctx);
        } else if (ctx.logicalOperator().XOR() != null) {
            push(left + " XOR " + right, ctx);
        } else {
            throw SQLParseException.from("Unexpected operator: " + ctx.logicalOperator().getText());
        }
    }

    @Override
    public void exitComparisonExpression(CockroachParser.ComparisonExpressionContext ctx) {
        String right = pop(String.class, ctx);
        String left = pop(String.class, ctx);

        if (ctx.comparisonOperator().GE() != null) {
            push(left + " >= " + right, ctx);
        } else if (ctx.comparisonOperator().LE() != null) {
            push(left + " <= " + right, ctx);
        } else if (ctx.comparisonOperator().EQUALS() != null) {
            push(left + " = " + right, ctx);
        } else if (ctx.comparisonOperator().GT() != null) {
            push(left + " > " + right, ctx);
        } else if (ctx.comparisonOperator().LT() != null) {
            push(left + " < " + right, ctx);
        } else if (ctx.comparisonOperator().NE() != null) {
            push(left + " != " + right, ctx);
        } else {
            throw SQLParseException.from("Unexpected operator: " + ctx.comparisonOperator().getText());
        }
    }

    @Override
    public void exitMultiplyOrDivideExpression(CockroachParser.MultiplyOrDivideExpressionContext ctx) {
        String right = pop(String.class, ctx);
        String left = pop(String.class, ctx);

        if (ctx.ASTERISK() != null) {
            push(left + " * " + right, ctx);
        } else if (ctx.DIV() != null) {
            push(left + " / " + right, ctx);
        } else if (ctx.MOD() != null) {
            push(left + " % " + right, ctx);
        }
    }

    @Override
    public void exitPlusOrMinusExpression(CockroachParser.PlusOrMinusExpressionContext ctx) {
        String right = pop(String.class, ctx);
        String left = pop(String.class, ctx);

        if (ctx.PLUS() != null) {
            push(left + " + " + right, ctx);
        } if (ctx.MINUS() != null) {
            push(left + " - " + right, ctx);
        }
    }

    @Override
    public void exitLiteral(CockroachParser.LiteralContext ctx) {
        push(ctx.getText(), ctx);
    }

    @Override
    public void exitIdentifier(CockroachParser.IdentifierContext ctx) {
        if (!(ctx.getParent() instanceof CockroachParser.FunctionNameContext)) {
            String id = ctx.getText();
            push(tableName + "." + id, ctx);
        }
    }

    @Override
    public void exitPlaceholder(CockroachParser.PlaceholderContext ctx) {
        String id = ctx.getText();
        if (ctx.QUESTION() != null) {
            id = parameterPrefix + parameterIndex.incrementAndGet();
        }
        push(fromQueryAlias + "." + id, ctx);
        placeHolders.add(id);
    }

    @Override
    public void exitFunctionCall(CockroachParser.FunctionCallContext ctx) {
        String fnName = ctx.functionName().getText();

        List<String> args = new ArrayList<>();

        Optional.ofNullable(ctx.expressionList()).ifPresent(
                expressionListContext -> expressionListContext.expression()
                        .forEach(expressionContext -> args.add(pop(String.class, ctx))));

        Collections.reverse(args);

        final StringBuilder sb = new StringBuilder()
                .append(fnName)
                .append("(");

        int c = 0;
        for (String a : args) {
            if (c++ > 0) {
                sb.append(", ");
            }
            sb.append(a);
        }
        sb.append(")");

        push(sb.toString(), ctx);
    }

    @Override
    public void exitNestedExpression(CockroachParser.NestedExpressionContext ctx) {
        String expr = pop(String.class, ctx);
        push("(" + expr + ")", ctx);
    }
}
