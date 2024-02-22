package io.cockroachdb.jdbc.demo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.postgresql.PGProperty;

import com.zaxxer.hikari.HikariDataSource;

import io.cockroachdb.jdbc.CockroachDriver;
import io.cockroachdb.jdbc.CockroachProperty;
import io.cockroachdb.jdbc.retry.EmptyRetryListener;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;
import io.cockroachdb.jdbc.util.ExceptionUtils;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

public class CockroachDriverDemo {
    private static void printf(AnsiColor color, String format, Object... args) {
        System.out.printf("%s", color.getCode());
        System.out.printf(format, args);
        System.out.printf("%s", AnsiColor.RESET.getCode());
    }

    private static void println(AnsiColor color, String format, Object... args) {
        printf(color, format, args);
        System.out.println();
    }

    private boolean printErrors;

    boolean clientRetry;

    boolean sfu;

    private List<Long> findUserAccountIDs(DataSource ds) {
        return JdbcUtils.select(ds, "SELECT id,type FROM bank_account WHERE type='U'", resultSet -> {
            List<Long> ids = new ArrayList<>();
            while (resultSet.next()) {
                ids.add(resultSet.getLong(1));
            }
            return ids;
        });
    }

    private List<Long> findSystemAccountIDs(DataSource ds) {
        return JdbcUtils.select(ds, "SELECT id,type FROM bank_account WHERE type='S'", resultSet -> {
            List<Long> ids = new ArrayList<>();
            while (resultSet.next()) {
                ids.add(resultSet.getLong(1));
            }
            return ids;
        });
    }

    private void updateBalance(Connection conn, Long id, BigDecimal balance) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE bank_account SET balance = ?, updated_at=clock_timestamp() WHERE id = ?")) {
            ps.setBigDecimal(1, balance);
            ps.setLong(2, id);
            if (ps.executeUpdate() != 1) {
                throw new DataAccessException("Rows affected != 1  for " + id);
            }
        }
    }

    private BigDecimal readBalance(Connection conn, Long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM bank_account WHERE id = ?")) {
            ps.setLong(1, id);

            try (ResultSet res = ps.executeQuery()) {
                if (!res.next()) {
                    throw new DataAccessException("Account not found: " + id);
                }
                return res.getBigDecimal("balance");
            }
        }
    }

    private ConnectionCallback<Void> transferFunds(List<AccountLeg> legs) {
        return conn -> {
            if (sfu) {
                conn.createStatement().execute("SET implicitSelectForUpdate = true");
            }

            BigDecimal checksum = BigDecimal.ZERO;

            for (AccountLeg leg : legs) {
                BigDecimal balance = readBalance(conn, leg.getId());
                BigDecimal newBalance = balance.add(leg.getAmount());
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException(
                            "Negative balance outcome " + newBalance.toPlainString() + " for account ID "
                                    + leg.getId());
                }
                updateBalance(conn, leg.getId(), newBalance);
                checksum = checksum.add(leg.getAmount());
            }

            if (checksum.compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException(
                        "Sum of account legs must equal zero (got " + checksum.toPlainString() + ")");
            }

            return null;
        };
    }

    /**
     * Run the demo workload using the given datasource.
     *
     * @param ds the datasource
     */
    public void run(DataSource ds) {
        String version = JdbcUtils.select(ds, "select version()", resultSet -> {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
            return null;
        });

        println(AnsiColor.BRIGHT_YELLOW, "Connected to \"%s\"", version);

        final List<Long> systemAccountIDs = findSystemAccountIDs(ds);
        if (systemAccountIDs.isEmpty()) {
            throw new IllegalStateException("No system accounts found!");
        }

        final List<Long> userAccountIDs = findUserAccountIDs(ds);
        if (userAccountIDs.isEmpty()) {
            throw new IllegalStateException("No user accounts found!");
        }

        println(AnsiColor.BRIGHT_YELLOW, "Found %,d system and %,d user accounts - scheduling workers for each",
                systemAccountIDs.size(),
                userAccountIDs.size());

        // Unbounded thread pool is fine since the connection pool will throttle
        final ExecutorService unboundedPool = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors());

        final Deque<Future<?>> futures = new ArrayDeque<>();

        final Instant callTime = Instant.now();

        Collections.shuffle(systemAccountIDs);
        Collections.shuffle(userAccountIDs);

        userAccountIDs.forEach(userAccountId -> {
            systemAccountIDs.forEach(systemAccountId -> {
                futures.add(unboundedPool.submit(() -> {
                    BigDecimal amount = new BigDecimal("100.00");

                    List<AccountLeg> legs = new ArrayList<>();
                    legs.add(new AccountLeg(systemAccountId, amount.negate()));
                    legs.add(new AccountLeg(userAccountId, amount));

                    if (clientRetry) {
                        return JdbcUtils.executeWithinTransactionWithRetry(ds, transferFunds(legs));
                    } else {
                        return JdbcUtils.executeWithinTransaction(ds, transferFunds(legs));
                    }
                }));
            });
        });

        println(AnsiColor.BRIGHT_YELLOW, "Scheduled %,d futures", futures.size());

        final int total = futures.size();
        int commits = 0;
        int rollbacks = 0;
        int violations = 0;

        while (!futures.isEmpty()) {
            if (futures.size() % 100 == 0) {
                println(AnsiColor.BRIGHT_YELLOW,
                        "Awaiting completion (%,d commits, %,d rollbacks, %,d violations, %,d remaining)", commits,
                        rollbacks, violations, futures.size());
            }
            try {
                futures.pop().get();
                commits++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                Throwable throwable = ExceptionUtils.getMostSpecificCause(e);
                if (throwable instanceof BusinessException) {
                    violations++;
                } else {
                    rollbacks++;
                }
                if (printErrors) {
                    println(AnsiColor.BRIGHT_RED, "%s", throwable);
                }
            }
        }

        unboundedPool.shutdownNow();

        println(AnsiColor.BRIGHT_GREEN, "%,d commits", commits);
        println(AnsiColor.BRIGHT_GREEN, "%,d rollbacks", rollbacks);
        printf(AnsiColor.BRIGHT_GREEN, "%.2f%% commit rate!",
                100 - (rollbacks / (double) (Math.max(1, total))) * 100.0);
        println(rollbacks > 0 ? AnsiColor.BOLD_BRIGHT_RED : AnsiColor.BOLD_BRIGHT_GREEN, " %s",
                rollbacks > 0 ? "(╯°□°)╯︵ ┻━┻" : "¯\\\\_(ツ)_/¯");

        println(AnsiColor.BRIGHT_GREEN, "%,d rule violations", violations);
        printf(AnsiColor.BRIGHT_GREEN, "%.2f%% violation rate!",
                (violations / (double) (Math.max(1, total))) * 100.0);
        println(violations > 0 ? AnsiColor.BOLD_BRIGHT_RED : AnsiColor.BOLD_BRIGHT_GREEN, " %s",
                violations > 0 ? "(╯°□°)╯︵ ┻━┻" : "¯\\\\_(ツ)_/¯");

        println(AnsiColor.BRIGHT_GREEN, "%s execution time", Duration.between(callTime, Instant.now()));
        println(AnsiColor.BRIGHT_GREEN, "%.2f avg TPS",
                ((double) total / (double) Duration.between(callTime, Instant.now()).toSeconds()));
    }

    public static void usage(String message) {
        if (!"".equals(message)) {
            println(AnsiColor.BOLD_BRIGHT_RED, "Unrecognized option: '" + message + "'");
        }
        System.out.println();
        System.out.println();

        println(AnsiColor.BOLD_BRIGHT_WHITE, "Usage: java -jar cockroachdb-jdbc-demo.jar [options]");
        println(AnsiColor.BOLD_BRIGHT_WHITE, "Options include: (defaults in parenthesis)");
        println(AnsiColor.BRIGHT_CYAN,
                "--url <url>               Connection URL (jdbc:cockroachdb://localhost:26257/defaultdb)");
        println(AnsiColor.BRIGHT_CYAN, "--user <user>             Login user name (root)");
        println(AnsiColor.BRIGHT_CYAN, "--password <secret>       Login password (empty)");
        println(AnsiColor.BRIGHT_CYAN,
                "--isolation <level>       Transaction isolation level when using psql (READ_COMMITTED)");
        println(AnsiColor.BRIGHT_CYAN, "--poolSize <N>            Connection pool size (client vCPUs x 2)");
        println(AnsiColor.BRIGHT_CYAN,
                "--sfu <txn|stmt>          Enable implicit SFU either at transaction level or statement level");
        println(AnsiColor.BRIGHT_CYAN, "--retry <client|driver>   Enable transaction retries client or driver side");
        println(AnsiColor.BRIGHT_CYAN, "--trace                   Enable SQL trace logging");
        println(AnsiColor.BRIGHT_CYAN, "--verbose                 Verbose logging to console");
        println(AnsiColor.BRIGHT_CYAN, "--printErrors             Print SQL rollback errors to console");

        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        String url = "jdbc:cockroachdb://localhost:26257/defaultdb?sslmode=disable";

        String username = "root";

        String password = "";

        String isolationLevel = "READ_COMMITTED";

        boolean traceSQL = false;

        boolean sfuConnectionScope = false;

        boolean sfuTransactionScope = false;

        boolean driverRetry = false;

        boolean clientRetry = false;

        int poolSize = Runtime.getRuntime().availableProcessors() * 2;

        boolean verbose = false;

        boolean printErrors = false;

        LinkedList<String> argsList = new LinkedList<>(Arrays.asList(args));
        while (!argsList.isEmpty()) {
            String arg = argsList.pop();
            if (arg.equals("--poolSize")) {
                poolSize = Integer.parseInt(argsList.pop());
            } else if (arg.equals("--url")) {
                if (argsList.isEmpty()) {
                    usage("Expected URL after: " + arg);
                } else {
                    url = argsList.pop();
                }
            } else if (arg.equals("--user")) {
                if (argsList.isEmpty()) {
                    usage( "Expected username after: " + arg);
                } else {
                    username = argsList.pop();
                }
            } else if (arg.equals("--password")) {
                if (argsList.isEmpty()) {
                    usage( "Expected password after: " + arg);
                } else {
                    password = argsList.pop();
                }
            } else if (arg.equals("--isolation")) {
                if (argsList.isEmpty()) {
                    usage( "Expected isolation level name after: " + arg);
                } else {
                    isolationLevel = argsList.pop();
                }
            } else if (arg.equals("--trace")) {
                traceSQL = true;
            } else if (arg.startsWith("--sfu")) {
                String mode = argsList.pop();
                if ("txn".equals(mode)) {
                    sfuTransactionScope = true;
                } else {
                    sfuConnectionScope = true;
                }
            } else if (arg.startsWith("--retry")) {
                String mode = argsList.pop();
                if ("client".equals(mode)) {
                    clientRetry = true;
                } else {
                    driverRetry = true;
                }
            } else if (arg.equals("--verbose")) {
                verbose = true;
            } else if (arg.equals("--printErrors")) {
                printErrors = true;
            } else if (arg.equals("--help")) {
                usage("");
            } else if (arg.startsWith("--")) {
                usage( "Unrecognized option: '" + arg + "'");
            } else {
                usage( "Unrecognized argument: '" + arg + "'");
            }
        }

        println(AnsiColor.BOLD_BRIGHT_WHITE, "Starting JDBC driver demo");

        final HikariDataSource hikariDS = new HikariDataSource();
        hikariDS.setJdbcUrl(url);
        hikariDS.setUsername(username);
        hikariDS.setPassword(password);
        hikariDS.setAutoCommit(true);
        hikariDS.setMaximumPoolSize(poolSize);
        hikariDS.setMinimumIdle(poolSize / 2);
        hikariDS.setMaxLifetime(TimeUnit.MINUTES.toMillis(3));
        hikariDS.setIdleTimeout(TimeUnit.SECONDS.toMillis(60));
        hikariDS.setConnectionTimeout(TimeUnit.SECONDS.toMillis(60));
        hikariDS.addDataSourceProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(), "true");

        if (url.startsWith(CockroachDriver.DRIVER_PREFIX)) {
            hikariDS.setDriverClassName(CockroachDriver.class.getName());
            hikariDS.addDataSourceProperty(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getName(), sfuConnectionScope);
            hikariDS.addDataSourceProperty(CockroachProperty.RETRY_TRANSIENT_ERRORS.getName(), driverRetry);
            hikariDS.addDataSourceProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "30");
            hikariDS.addDataSourceProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "15000");

            if (verbose) {
                hikariDS.addDataSourceProperty(CockroachProperty.RETRY_LISTENER_CLASSNAME.getName(),
                        LoggingRetryListener.class.getName());
            } else {
                hikariDS.addDataSourceProperty(CockroachProperty.RETRY_LISTENER_CLASSNAME.getName(),
                        EmptyRetryListener.class.getName());
            }

            println(AnsiColor.BRIGHT_WHITE, "SFU: %s", sfuConnectionScope);
            println(AnsiColor.BRIGHT_WHITE, "driverRetry: %s", driverRetry);
        } else {
            hikariDS.setTransactionIsolation("TRANSACTION_" + isolationLevel);
        }

        println(AnsiColor.BRIGHT_WHITE, "url: %s", url);
        println(AnsiColor.BRIGHT_WHITE, "username: %s", username);
        println(AnsiColor.BRIGHT_WHITE, "password: ***", password);

        DataSource ds = traceSQL
                ? ProxyDataSourceBuilder.create(hikariDS)
                .asJson()
                .logQueryBySlf4j(SLF4JLogLevel.TRACE, "io.cockroachdb.jdbc.SQL_TRACE")
                .multiline()
                .build()
                : hikariDS;

        SchemaSupport.setupSchema(ds);

        CockroachDriverDemo demo = new CockroachDriverDemo();
        demo.printErrors = printErrors;
        demo.clientRetry = clientRetry;
        demo.sfu = sfuTransactionScope;
        demo.run(ds);
    }
}
