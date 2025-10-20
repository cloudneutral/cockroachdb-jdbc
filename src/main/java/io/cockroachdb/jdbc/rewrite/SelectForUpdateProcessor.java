package io.cockroachdb.jdbc.rewrite;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cockroachdb.jdbc.util.Assert;

/**
 * A query processor that appends {@code FOR UPDATE} to SELECT queries when qualified.
 *
 * @author Kai Niemi
 */
public class SelectForUpdateProcessor implements QueryProcessor {
    /**
     * Singleton instance of this processor.
     */
    public static final SelectForUpdateProcessor INSTANCE = new SelectForUpdateProcessor();

    private static final Set<String> AGGREGATE_FUNCTIONS = new HashSet<>(Arrays.asList(
            "array_agg",
            "array_cat_agg",
            "avg",
            "bit_and",
            "bit_or",
            "bool_and",
            "bool_or",
            "concat_agg",
            "corr",
            "count",
            "count_rows",
            "covar_pop",
            "covar_samp",
            "every",
            "json_agg",
            "json_object_agg",
            "jsonb_agg",
            "jsonb_object_agg",
            "max",
            "min",
            "percentile_cont",
            "percentile_disc",
            "regr_avgx",
            "regr_avgy",
            "regr_count",
            "regr_intercept",
            "regr_r2",
            "regr_slope",
            "regr_sxx",
            "regr_sxy",
            "regr_sxy",
            "regr_syy",
            "sqrdiff",
            "st_collect",
            "st_extent",
            "st_makeline",
            "st_memcollect",
            "st_memunion",
            "st_union",
            "stddev",
            "stddev_pop",
            "stddev_samp",
            "string_agg",
            "sum",
            "sum_int",
            "var_pop",
            "var_samp",
            "variance",
            "xor_agg",
            "xor_agg"
    ));

    // Simple matching for identifier and open parenthesis
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("(\\w+)\\(", Pattern.CASE_INSENSITIVE);

    @Override
    public String processQuery(Connection connection, String query)
            throws SQLException {
        Assert.notNull(query, "Query is null");

        String queryLC = query.toLowerCase();
        if (queryLC.startsWith("select")) {
            if (connection.isReadOnly()
                    || hasAOST(queryLC)
                    || hasForUpdate(queryLC)
                    || hasAggregateFunction(queryLC)
                    || hasGroupBy(queryLC)
                    || hasDistinct(queryLC)
                    || hasSystemCatalogSchema(queryLC)) {
                return query;
            }
            // Apply SFU
            if (query.endsWith(";")) {
                query = query.replaceFirst(";", " FOR UPDATE;");
            } else {
                query = query + " FOR UPDATE";
            }
            return query;
        } else {
            return query;
        }
    }

    protected boolean hasAggregateFunction(String query) {
        Matcher m = FUNCTION_PATTERN.matcher(query);
        while (m.find()) {
            String g = m.group(1);
            if (AGGREGATE_FUNCTIONS.contains(g)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Implies read-only transaction.
     */
    protected boolean hasAOST(String query) {
        return query.contains("as of system time");
    }

    protected boolean hasGroupBy(String query) {
        return query.contains("group by");
    }

    protected boolean hasDistinct(String query) {
        return query.contains("distinct");
    }

    protected boolean hasForUpdate(String query) {
        return query.contains("for update"); // can have skip locked / nowait
    }

    protected boolean hasSystemCatalogSchema(String query) {
        return query.contains("crdb_internal.")
                || query.contains("information_schema.")
                || query.contains("pg_catalog.")
                || query.contains("pg_extension.");
    }

    @Override
    public boolean isTransactionScoped() {
        return false;
    }
}
