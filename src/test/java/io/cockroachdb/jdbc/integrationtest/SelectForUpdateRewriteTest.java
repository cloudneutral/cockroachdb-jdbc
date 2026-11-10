package io.cockroachdb.jdbc.integrationtest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.cockroachdb.jdbc.rewrite.SelectForUpdateProcessor;

/**
 * Verifies that queries accepted by {@link SelectForUpdateProcessor} remain valid CockroachDB SQL
 * after rewriting, by executing each rewritten query in an explicit transaction.
 */
public class SelectForUpdateRewriteTest extends AbstractIntegrationTest {
    private record Query(String sql, Object... params) {
    }

    private static final List<Query> ELIGIBLE = List.of(
            new Query("select * from sfu_account"),
            new Query("SELECT * FROM sfu_account"),
            new Query("select all * from sfu_account"),
            new Query("select sfu_account.* from sfu_account"),
            new Query("select *, balance from public.sfu_account"),
            new Query("select * from sfu_account where id = ?", 1),
            new Query("select * from sfu_account where id in (?, ?, ?)", 1, 2, 3),
            new Query("select * from sfu_account where id not in (1, 2)"),
            new Query("select * from sfu_account where balance between ? and ?", 0, 100),
            new Query("select * from sfu_account where name like ? or name not ilike 'a%'", "b%"),
            new Query("select * from sfu_account where closed is not null and active is true"),
            new Query("select * from sfu_account where id = ?::int and amount >= -1.5e2", "1"),
            new Query("select * from sfu_account where not active and balance + ? > 0", 1),
            new Query("select id, balance, name from sfu_account"),
            new Query("select a.id as account_id, a.balance bal from sfu_account a where a.id = ?", 1),
            new Query("select a.id from sfu_account as a"),
            new Query("select balance * 2 as doubled, -balance from sfu_account"),
            new Query("select \"group\", \"select\" from \"Sfu From\" where \"group\" = ?", 1),
            new Query("select * from sfu_account order by id"),
            new Query("select * from sfu_account order by balance desc nulls last, id asc"),
            new Query("select * from sfu_account where id > ? order by id limit 10", 0),
            new Query("select * from sfu_account limit ? offset ?", 10, 0),
            new Query("select * from sfu_account offset 1 rows limit all"),
            new Query("select * from sfu_account where (id = ? or id = ?) and (balance > 0)", 1, 2),
            new Query("select * from sfu_account where ((id = 1) or (id = 2 and (active = true)))"),
            new Query("select * from sfu_account -- trailing"),
            new Query("select * from sfu_account /* trailing */"),
            new Query("/* leading */ select * from sfu_account"),
            new Query("select * /* inline */ from sfu_account where name = 'x'"),
            new Query("select * from sfu_account\n-- note\n"),
            new Query("select * from sfu_account;"),
            new Query("select * from sfu_account where name = 'it''s; fine'"),
            new Query("select * from sfu_account where name = E'it\\'s; fine'"),
            new Query("select * from sfu_account where name = $$ group by ; ' $$"),
            new Query("select * from sfu_account /* outer /* inner */ group by */ where id = 1"),
            new Query("select * from sfu_account where id = 1 -- for update")
    );

    @BeforeEach
    public void setupSchema() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("create table if not exists sfu_account (id int primary key, name string, region string, "
                    + "balance decimal(10,2), closed timestamptz, active bool, amount decimal)");
            s.execute("upsert into sfu_account values (1, 'a', 'eu', 10.00, null, true, 1.0), "
                    + "(2, 'b', 'us', 20.00, now(), false, 2.0)");
            s.execute("create table if not exists \"Sfu From\" (\"group\" int primary key, \"select\" string)");
            s.execute("upsert into \"Sfu From\" values (1, 'x')");
        }
    }

    @Test
    public void whenExecutingRewrittenQueries_expectValidSQL() throws SQLException {
        List<Executable> checks = new ArrayList<>();

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);

            for (Query query : ELIGIBLE) {
                String rewritten = SelectForUpdateProcessor.INSTANCE.processQuery(c, query.sql());

                checks.add(() -> Assertions.assertNotEquals(query.sql(), rewritten,
                        "Expected rewrite of: " + query.sql()));
                checks.add(() -> execute(c, rewritten, query.params()));
            }

            Assertions.assertAll(checks);
        }
    }

    private void execute(Connection connection, String sql, Object... params) throws SQLException {
        try {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // drain
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLException("Rewritten query failed: " + sql, e.getSQLState(), e);
        } finally {
            connection.rollback();
        }
    }
}
