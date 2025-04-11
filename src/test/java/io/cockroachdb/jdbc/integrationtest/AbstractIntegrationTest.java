package io.cockroachdb.jdbc.integrationtest;

import java.io.Closeable;
import java.io.IOException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.PGProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import net.ttddyy.dsproxy.listener.logging.DefaultQueryLogEntryCreator;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import io.cockroachdb.jdbc.CockroachDriver;
import io.cockroachdb.jdbc.CockroachProperty;
import io.cockroachdb.jdbc.integrationtest.support.ScriptExecutor;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tags(value = {
        @Tag("integration-test")
})
public abstract class AbstractIntegrationTest {
    private static final Logger sqlTraceLogger = LoggerFactory.getLogger("io.cockroachdb.jdbc.SQL_TRACE");

    protected static DataSource dataSource;

    @BeforeAll
    public static void beforeAll() {
        HikariDataSource ds = hikariDataSource();

        DefaultQueryLogEntryCreator creator = new DefaultQueryLogEntryCreator();
        creator.setMultiline(false);

        SLF4JQueryLoggingListener listener = new SLF4JQueryLoggingListener();
        listener.setLogger(sqlTraceLogger);
        listener.setLogLevel(SLF4JLogLevel.TRACE);
        listener.setQueryLogEntryCreator(creator);

        AbstractIntegrationTest.dataSource = sqlTraceLogger.isTraceEnabled()
                ? ProxyDataSourceBuilder
                .create(ds)
                .asJson()
                .listener(listener)
                .build()
                : ds;
    }

    private static HikariDataSource hikariDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(System.getProperty("datasource.url", "jdbc:cockroachdb://localhost:26257/defaultdb?sslmode=disable"));
//        ds.setJdbcUrl(System.getProperty("datasource.url", "jdbc:postgresql://192.168.1.99:26257/defaultdb?sslmode=disable"));
        ds.setUsername(System.getProperty("datasource.user", "root"));
        ds.setPassword(System.getProperty("datasource.password", null));

        ds.setMaximumPoolSize(64);
        ds.setMinimumIdle(0);
        ds.setInitializationFailTimeout(-1);
        ds.setTransactionIsolation("TRANSACTION_SERIALIZABLE");
        ds.setAutoCommit(true);

        ds.addDataSourceProperty(PGProperty.APPLICATION_NAME.getName(), "cockroachdb-jdbc");
        ds.addDataSourceProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getName(), "false");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCH_ARRAYS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCHED_INSERTS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCHED_UPSERTS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCHED_UPDATES.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.RETRY_TRANSIENT_ERRORS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.RETRY_CONNECTION_ERRORS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "32");
        ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "15s");

        return ds;
    }


    @AfterAll
    public static void afterAll() throws IOException {
        ((Closeable) dataSource).close();
    }

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private LoggingRetryListener loggingRetryListener;

    protected LoggingRetryListener getLoggingRetryListener() {
        return loggingRetryListener;
    }

    @BeforeEach
    public void beforeEachTest() {
        this.loggingRetryListener = new LoggingRetryListener();
        CockroachDriver.setRetryListenerSupplier(() -> loggingRetryListener);
        ScriptExecutor.executeBeforeScript(getClass(), dataSource);
    }
}
