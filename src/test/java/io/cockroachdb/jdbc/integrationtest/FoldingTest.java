package io.cockroachdb.jdbc.integrationtest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cockroachdb.jdbc.integrationtest.support.ConnectionTemplate;

@Disabled
public class FoldingTest extends AbstractIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(FoldingTest.class);

    private static final int CONCURRENCY = 32;

    @RepeatedTest(value = 10)
    public void givenExplicitTransaction_thenExpectSuccess() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        IntStream.rangeClosed(1, CONCURRENCY).forEach(value -> {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);

                    UUID id = UUID.randomUUID();

                    ConnectionTemplate.from(connection).execute("""
                            insert into transfer (id) values (?) returning id,booking_date
                            """, id);
                    ConnectionTemplate.from(connection).execute("""
                            insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                values (?, '10000000-0000-0000-0000-000000000000', 75.00,
                                        (select balance + 75.00 from account where id = '10000000-0000-0000-0000-000000000000'))
                            """, id);
                    ConnectionTemplate.from(connection).execute("""
                            insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                values (?, '20000000-0000-0000-0000-000000000000', -75.00,
                                        (select balance - 75.00 from account where id = '20000000-0000-0000-0000-000000000000'))
                            """, id);
                    ConnectionTemplate.from(connection).execute("""
                            update account set balance = balance + 75.00 where id = '10000000-0000-0000-0000-000000000000'
                            """);
                    ConnectionTemplate.from(connection).execute("""
                            update account set balance = balance - 75.00 where id = '20000000-0000-0000-0000-000000000000'
                            """);

                    connection.commit();
                } catch (SQLException e) {
                    logger.error("", e);
                }
            });
            futures.add(f);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @RepeatedTest(value = 10)
    public void givenImplicitTransaction_thenExpectSuccess() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        IntStream.rangeClosed(1, CONCURRENCY).forEach(value -> {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(true);

                    ConnectionTemplate.from(connection).execute("""
                            with head as (
                                insert into transfer (id) values (gen_random_uuid())
                                    returning id,booking_date),
                                 item1 as (
                                     insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                         values ((select id from head),
                                                 '10000000-0000-0000-0000-000000000000',
                                                 75.00,
                                                 (select balance + 75.00 from account where id = '10000000-0000-0000-0000-000000000000'))
                                         returning transfer_id),
                                 item2 as (
                                     insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                         values ((select id from head),
                                                 '20000000-0000-0000-0000-000000000000',
                                                 -75.00,
                                                 (select balance - 75.00 from account where id = '20000000-0000-0000-0000-000000000000'))
                                         returning transfer_id)
                            update account
                            set balance=account.balance + dt.balance
                            from (select unnest(array [75, -75])                                                                                   as balance,
                                         unnest(array ['10000000-0000-0000-0000-000000000000'::uuid,'20000000-0000-0000-0000-000000000000'::uuid]) as id) as dt
                            where account.id = dt.id
                            returning account.id, account.balance
                            """);
                } catch (SQLException e) {
                    logger.error("", e);
                }
            });
            futures.add(f);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
