package io.cockroachdb.jdbc.rewrite;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;

import io.cockroachdb.jdbc.parser.CockroachSelectLexer;
import io.cockroachdb.jdbc.parser.CockroachSelectParser;
import io.cockroachdb.jdbc.util.Assert;

/**
 * A query processor that appends {@code FOR UPDATE} to qualified SELECT queries.
 * <p>
 * Qualification is positive and conservative: a query is only rewritten if it parses with the
 * {@code CockroachSelectParser} grammar, which describes a small subset of simple single-table
 * SELECT statements without function calls, and passes the additional checks in this class
 * (no system schemas, bounded nesting depth). Anything else passes through unchanged. False
 * negatives are preferred over rewriting valid SQL into invalid SQL.
 * <p>
 * The original SQL text is preserved exactly, except for the inserted locking clause.
 *
 * @author Kai Niemi
 */
public class SelectForUpdateProcessor implements QueryProcessor {
    /**
     * Singleton instance of this processor.
     */
    public static final SelectForUpdateProcessor INSTANCE = new SelectForUpdateProcessor();

    private static final String LOCKING_CLAUSE = " FOR UPDATE";

    /**
     * Maximum parenthesis nesting depth, beyond which a query is not qualified (bounds parser recursion).
     */
    private static final int MAX_DEPTH = 32;

    /**
     * Virtual or system schemas that don't support locking.
     */
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "crdb_internal", "information_schema", "pg_catalog", "pg_extension");

    @Override
    public String processQuery(Connection connection, String query)
            throws SQLException {
        Assert.notNull(query, "Query is null");

        List<? extends Token> tokens = tokenize(query);
        if (tokens == null) {
            return query;
        }

        if (connection.isReadOnly()) {
            return query;
        }

        int offset = qualify(query, tokens);
        if (offset < 0) {
            return query;
        }

        return query.substring(0, offset) + LOCKING_CLAUSE + query.substring(offset);
    }

    @Override
    public boolean isTransactionScoped() {
        return false;
    }

    /**
     * Determine whether a query qualifies for a {@code FOR UPDATE} rewrite.
     *
     * @param query the SQL query
     * @return the offset in the original query where the locking clause is to be inserted,
     * or -1 if the query does not qualify
     */
    static int qualify(String query) {
        List<? extends Token> tokens = tokenize(query);
        return tokens == null ? -1 : qualify(query, tokens);
    }

    /**
     * @return the significant tokens of a SELECT statement, or {@code null} if the query is
     * not a SELECT, contains unsupported tokens or exceeds the nesting depth
     */
    private static List<? extends Token> tokenize(String query) {
        CockroachSelectLexer lexer = new CockroachSelectLexer(CharStreams.fromString(query));
        lexer.removeErrorListeners();

        List<? extends Token> tokens;
        try {
            tokens = lexer.getAllTokens();
        } catch (RuntimeException e) {
            return null;
        }

        Token first = null;
        int depth = 0;
        for (Token token : tokens) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && first == null) {
                first = token;
            }
            switch (token.getType()) {
                case CockroachSelectLexer.BLOCK_COMMENT:
                    if (!isWellFormedComment(token.getText())) {
                        return null;
                    }
                    break;
                case CockroachSelectLexer.UNSUPPORTED:
                case CockroachSelectLexer.UNSUPPORTED_NUMBER:
                    return null;
                case CockroachSelectLexer.LPAREN:
                    if (++depth > MAX_DEPTH) {
                        return null;
                    }
                    break;
                case CockroachSelectLexer.RPAREN:
                    depth--;
                    break;
                default:
                    break;
            }
        }

        if (first == null || first.getType() != CockroachSelectLexer.SELECT) {
            return null;
        }
        return tokens;
    }

    /**
     * Verify that a block comment token ends exactly where its (nested) comment ends, using the
     * same nesting rules as CockroachDB.
     */
    private static boolean isWellFormedComment(String text) {
        int depth = 0;
        int i = 0;
        while (i < text.length() - 1) {
            if (text.startsWith("/*", i)) {
                depth++;
                i += 2;
            } else if (text.startsWith("*/", i)) {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i == text.length();
                }
            } else {
                i++;
            }
        }
        return false;
    }

    private static int qualify(String query, List<? extends Token> tokens) {
        CockroachSelectParser parser = new CockroachSelectParser(
                new CommonTokenStream(new ListTokenSource(tokens)));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        // SLL may reject input that full LL would accept, which only results in a false negative
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);

        final CockroachSelectParser.RootContext root;
        try {
            root = parser.root();
        } catch (RuntimeException e) {
            return -1; // Not within the supported subset
        }

        if (isSystemRelation(root.selectStatement().tableReference().qualifiedName().name())) {
            return -1;
        }

        // Insert before the first trailing terminator, otherwise after the last token
        // (thereby preserving any trailing whitespace and comments)
        int codePointOffset = root.SEMI().isEmpty()
                ? root.selectStatement().getStop().getStopIndex() + 1
                : root.SEMI(0).getSymbol().getStartIndex();

        // Token indexes are code points, not UTF-16 chars
        return query.offsetByCodePoints(0, codePointOffset);
    }

    private static boolean isSystemRelation(List<CockroachSelectParser.NameContext> parts) {
        for (int i = 0; i < parts.size(); i++) {
            String part = identifier(parts.get(i));
            if (SYSTEM_SCHEMAS.contains(part)) {
                return true;
            }
            if (i == 0 && parts.size() > 1 && "system".equals(part)) {
                return true; // system database
            }
        }
        // pg_catalog is implicitly on the search path
        return parts.size() == 1 && identifier(parts.get(0)).startsWith("pg_");
    }

    /**
     * @return normalized identifier (quoted identifiers unquoted, unquoted folded to lower case)
     */
    private static String identifier(CockroachSelectParser.NameContext name) {
        String text = name.getText();
        if (name.QUOTED_IDENTIFIER() != null) {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text.toLowerCase(Locale.ROOT);
    }
}
