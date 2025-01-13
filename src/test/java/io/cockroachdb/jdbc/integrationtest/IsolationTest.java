package io.cockroachdb.jdbc.integrationtest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.cockroachdb.jdbc.CockroachConnection;
import io.cockroachdb.jdbc.integrationtest.support.ConnectionTemplate;
import io.cockroachdb.jdbc.integrationtest.support.DatabaseFixture;
import io.cockroachdb.jdbc.retry.ConcurrentUpdateException;

/**
 * Integration test verifying guards against concurrency anomalies under
 * serializable (1SR) isolation in CockroachDB.
 * <p>
 * Known anomalies under test: G0,G1a,G1b,G1c,OTV,PMP,P4,G-single,G2-item and G2
 * <p>
 * Based on Martin Kleppman's Hermitage (https://github.com/ept/hermitage).
 */
@DatabaseFixture(beforeTestScript = "/db/isolation-ddl.sql")
public class IsolationTest extends AbstractAnomalyTest {
    private Connection openConnection() throws SQLException {
        Connection c = dataSource.getConnection();
        c.setAutoCommit(false);
        Assertions.assertTrue(c.isWrapperFor(CockroachConnection.class));
        return c;
    }

    /**
     * Pass dummy query and wait max-offset time to get old enough timestamp.
     */
    private void waitForMaxOffset(Connection c) {
        try {
            ConnectionTemplate.from(c).execute("show database");
            Thread.sleep(MAX_OFFSET_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assertions.fail(e);
        } catch (SQLException ex) {
            Assertions.fail(ex);
        }
    }

    @Test
    @Order(1)
    @Timeout(value = 10)
    public void given1SR_prevent_G0_WriteCycles() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();
        Assertions.assertNotSame(t1, t2);

        ConnectionTemplate.from(t1).update("update test set value=11 where id=1");

        // Blocks on t1
        Future<Integer> f2 = threadPool.submitAndWait(
                () -> ConnectionTemplate.from(t2).update("update test set value=12 where id=1"),
                MAX_OFFSET_MILLIS);

        ConnectionTemplate.from(t1).update("update test set value=21 where id=2");

        ConnectionTemplate.from(t1).select("select * from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(11, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(21, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t1.commit(); // unblocks t2

        Assertions.assertEquals(1, awaitFuture(f2));

        ConnectionTemplate.from(t2).update("update test set value=22 where id=2");
        t2.commit();

        ConnectionTemplate.from(t1).select("select * from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(12, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(22, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t1.close();
        t2.close();
    }

    @Test
    @Order(2)
    @Timeout(value = 10)
    public void given1SR_prevent_G1a_AbortedReads() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();
        Assertions.assertNotSame(t1, t2);

        waitForMaxOffset(t2);

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value=101 where id=1"));

        ConnectionTemplate.from(t2).select("select * from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t1.rollback();

        ConnectionTemplate.from(t2).select("select * from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t2.commit();

        t1.close();
        t2.close();
    }

    @Test
    @Order(3)
    @Timeout(value = 10)
    public void given1SR_prevent_G1b_IntermediateReads() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();
        Assertions.assertNotSame(t1, t2);

        waitForMaxOffset(t2);

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value=101 where id=1"));

        ConnectionTemplate.from(t2).select("select id,value from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        ConnectionTemplate.from(t1).update("update test set value=11 where id=1");
        t1.commit();

        ConnectionTemplate.from(t2).select("select * from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t2.commit();

        t1.close();
        t2.close();
    }

    @Test
    @Order(4)
    @Timeout(value = 10)
    public void given1SR_prevent_G1c_CircularInformationFlow() throws Exception {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        waitForMaxOffset(t2);

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value=11 where id=1"));
        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value=22 where id=2"));

        threadPool.submitAfterDelay(() -> {
            t1.commit();
            t1.close();
            return null;
        }, 1000);

        t2.commit();
        t2.close();

        Connection t3 = openConnection();
        ConnectionTemplate.from(t3).select("select * from test where id=2", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(22, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t3.commit();
        t3.close();

        Connection t4 = openConnection();
        ConnectionTemplate.from(t4).select("select * from test where id=1", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(11, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t4.commit();
        t4.close();
    }

    @Test
    @Order(5)
    @Timeout(value = 10)
    public void given1SR_prevent_OTV_ObservedTransactionVanishes() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();
        Connection t3 = openConnection();

        waitForMaxOffset(t2);
        waitForMaxOffset(t3);

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value=11 where id=1"));
        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value=19 where id=2"));
        Future<?> f1 = threadPool.submitAndWait(
                () -> ConnectionTemplate.from(t2).update("update test set value=12 where id=1"),
                MAX_OFFSET_MILLIS);

        t1.commit();

        awaitFuture(f1);

        ConnectionTemplate.from(t3).select("select * from test where id=1", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
        });

        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value=18 where id=2"));

        ConnectionTemplate.from(t3).select("select * from test where id=2", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
        });

        t2.commit();

        ConnectionTemplate.from(t3).select("select * from test where id=2", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
        });
        ConnectionTemplate.from(t3).select("select * from test where id=1", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
        });
        t3.commit();

        t1.close();
        t2.close();
        t3.close();
    }

    @Test
    @Order(6)
    @Timeout(value = 10)
    public void given1SR_prevent_PMP_PredicateManyPreceders() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test where value=30", rs -> {
            Assertions.assertFalse(rs.next());
        });

        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("insert into test (id, value) values(3, 30)"));

        t2.commit();

        ConnectionTemplate.from(t1).select("select * from test where value % 3 = 0", rs -> {
            Assertions.assertFalse(rs.next());
        });

        t1.commit();

        t1.close();
        t2.close();
    }

    @Test
    @Order(7)
    @Timeout(value = 10)
    public void given1SR_prevent_PMP_PredicateManyPrecedersForWritePredicates() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        Assertions.assertEquals(2, ConnectionTemplate.from(t1).update("update test set value = value + 10 where true"));

        Future<?> f1 = threadPool.submitAndWait(
                () -> ConnectionTemplate.from(t2).update("delete from test where value = 20"),
                MAX_OFFSET_MILLIS);

        t1.commit();

        awaitFuture(f1);

        t2.rollback();

        t1.close();
        t2.close();
    }

    @Test
    @Order(8)
    @Timeout(value = 10)
    public void given1SR_prevent_P4_LostUpdate() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test where id=1", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
        });
        ConnectionTemplate.from(t1).select("select * from test where id=2", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
        });

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value = 11 where id = 1"));

        Future<?> f1 = threadPool.submitAndWait(
                () -> ConnectionTemplate.from(t2).update("update test set value = 11 where id = 1"),
                MAX_OFFSET_MILLIS);

        t1.commit();

        awaitFuture(f1);

        t2.rollback();

        t1.close();
        t2.close();
    }

    @Test
    @Order(9)
    @Timeout(value = 10)
    public void given1SR_prevent_G_Single_ReadSkew() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test where id=1", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
        });
        ConnectionTemplate.from(t2).select("select * from test where id=1", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
        });
        ConnectionTemplate.from(t2).select("select * from test where id=2", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
        });

        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value = 12 where id = 1"));
        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value = 18 where id = 2"));

        t2.commit();

        ConnectionTemplate.from(t1).select("select * from test where id=2", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
        });

        t1.close();
        t2.close();
    }

    @Test
    @Order(10)
    @Timeout(value = 10)
    public void given1SR_prevent_G_Single_ReadSkew_Using_Predicate_Dependencies() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test where value % 5 = 0", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });
        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value = 12 where value = 10"));

        t2.commit();

        ConnectionTemplate.from(t1).select("select * from test where value % 3 = 0", rs -> {
            Assertions.assertFalse(rs.next());
        });

        t1.commit();

        t1.close();
        t2.close();
    }

    @Test
    @Order(11)
    @Timeout(value = 10)
    public void given1SR_prevent_G_Single_ReadSkew_Using_Write_Predicate() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test where id = 1", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });
        ConnectionTemplate.from(t2).select("select * from test", rs -> {

        });
        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value = 12 where id = 1"));
        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value = 18 where id = 2"));

        t2.commit();

        Assertions.assertThrows(
                SQLException.class, () -> ConnectionTemplate.from(t1).update("delete from test where value = 20"));

        t1.rollback();

        t1.close();
        t2.close();
    }

    @Test
    @Order(12)
    @Timeout(value = 10)
    public void given1SR_prevent_G2_Item_WriteSkew() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test where id in (1,2)", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        ConnectionTemplate.from(t2).select("select * from test where id in (1,2)", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value = 11 where id = 1"));

        t1.commit();
        t1.close();

        Assertions.assertThrows(
                ConcurrentUpdateException.class,
                () -> {
                    ConnectionTemplate.from(t2).update("update test set value = 22 where id = 2");
                    t2.commit();
                });

        t2.close();

        Connection t3 = openConnection();
        ConnectionTemplate.from(t3).select("select * from test where id in (1,2)", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(11, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t3.close();
    }

    @Test
    @Order(13)
    @Timeout(value = 10)
    public void given1SR_prevent_G2_AntiDependencyCycles() throws SQLException {
        Connection t1 = openConnection();
        Connection t2 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test where value % 3 = 0", rs -> {
            Assertions.assertFalse(rs.next());
        });

        ConnectionTemplate.from(t2).select("select * from test where value % 3 = 0", rs -> {
            Assertions.assertFalse(rs.next());
        });

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("insert into test (id, value) values(3, 30)"));
        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("insert into test (id, value) values(4, 42)"));

        threadPool.submitAfterDelay(() -> {
            Assertions.assertThrows(
                    SQLException.class, () -> t1.commit());
            return null;
        }, MAX_OFFSET_MILLIS);

        t2.commit();

        t1.close();
        t2.close();
    }

    @Test
    @Order(14)
    @Timeout(value = 10)
    public void given1SR_prevent_G2_Anti_Dependency_Cycles_With_Two_Anti_Dependency_Edges() throws SQLException {
        Connection t1 = openConnection();

        ConnectionTemplate.from(t1).select("select * from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(20, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        Connection t2 = openConnection();

        Assertions.assertEquals(1, ConnectionTemplate.from(t2).update("update test set value = value + 5 where id = 2"));

        t2.commit();

        Connection t3 = openConnection();

        ConnectionTemplate.from(t3).select("select * from test", rs -> {
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(1, rs.getInt(1));
            Assertions.assertEquals(10, rs.getInt(2));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(1));
            Assertions.assertEquals(25, rs.getInt(2));
            Assertions.assertFalse(rs.next());
        });

        t3.commit();

        Assertions.assertEquals(1, ConnectionTemplate.from(t1).update("update test set value = 0 where id = 1"));

        Assertions.assertThrows(
                SQLException.class, () -> t1.commit());

        t1.close();
        t2.close();
        t3.close();
    }
}
