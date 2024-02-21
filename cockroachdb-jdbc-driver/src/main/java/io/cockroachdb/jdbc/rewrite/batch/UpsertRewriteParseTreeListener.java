package io.cockroachdb.jdbc.rewrite.batch;

import io.cockroachdb.jdbc.rewrite.AbstractCockroachParserListener;
import io.cockroachdb.jdbc.rewrite.CockroachParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Parse tree listener for rewriting UPSERT statements to use batch arrays.
 *
 * @author Kai Niemi
 */
public class UpsertRewriteParseTreeListener extends AbstractCockroachParserListener {
    private final List<String> columnNames = new ArrayList<>();

    private final List<String> columnValues = new ArrayList<>();

    private String tableName;

    private final Consumer<String> consumer;

    public UpsertRewriteParseTreeListener(Consumer<String> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void exitUpsertStatement(CockroachParser.UpsertStatementContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("upsert into ")
                .append(tableName)
                .append(" (");

        int c = 0;
        for (String name : columnNames) {
            if (c++ > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }

        sb.append(") select ");

        Collections.reverse(columnValues);

        c = 0;
        for (String value : columnValues) {
            if (c > 0) {
                sb.append(", ");
            }
            if (value.equals("?")) {
                sb.append("unnest(?) as ").append(columnNames.get(c));
            } else {
                sb.append(value);
            }
            c++;
        }

        consumer.accept(sb.toString());
    }

    @Override
    public void exitTableName(CockroachParser.TableNameContext ctx) {
        this.tableName = ctx.getText();
    }

    @Override
    public void exitColumnName(CockroachParser.ColumnNameContext ctx) {
        if (!(ctx.getParent() instanceof CockroachParser.OptionalConflictExpressionContext)) {
            columnNames.add(pop(String.class, ctx));
        }
    }

    @Override
    public void exitValueList(CockroachParser.ValueListContext ctx) {
        ctx.atomList().atom()
                .forEach(atomContext -> columnValues.add(pop(String.class, ctx)));
    }

    @Override
    public void exitLiteral(CockroachParser.LiteralContext ctx) {
        push(ctx.getText(), ctx);
    }

    @Override
    public void exitIdentifier(CockroachParser.IdentifierContext ctx) {
        if (!(ctx.getParent() instanceof CockroachParser.FunctionNameContext)) {
            push(ctx.getText(), ctx);
        }
    }

    @Override
    public void exitPlaceholder(CockroachParser.PlaceholderContext ctx) {
        push(ctx.getText(), ctx);
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
