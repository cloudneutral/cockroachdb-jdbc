package io.cockroachdb.jdbc.test;

import javax.sql.DataSource;

import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.zaxxer.hikari.HikariDataSource;

import io.cockroachdb.jdbc.CockroachDataSource;
import io.cockroachdb.jdbc.CockroachProperty;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class
})
@ComponentScan(basePackageClasses = TestConfiguration.class)
@Configuration
public class TestConfiguration {
    @Autowired
    private Environment environment;

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Profile("ds-crdb")
    public CockroachDataSource cockroachDataSource() {
        CockroachDataSource ds = dataSourceProperties()
                .initializeDataSourceBuilder()
                .type(CockroachDataSource.class)
                .build();

        ds.setAutoCommit(true);

        ds.addDataSourceProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(), "true");
        ds.addDataSourceProperty(PGProperty.APPLICATION_NAME.getName(), "CockroachDB JDBC Driver Test (non-pooled)");
        ds.addDataSourceProperty(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getName(), "false");
        ds.addDataSourceProperty(CockroachProperty.RETRY_TRANSIENT_ERRORS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.RETRY_CONNECTION_ERRORS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "100"); // Set high for testing
        ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "15s");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCHED_UPDATES.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCHED_UPSERTS.getName(), "true");

        return ds;
    }

    @Bean
    @Profile("ds-hikari")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource hikariDataSource() {
        HikariDataSource ds = dataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        ds.setAutoCommit(true);

        ds.addDataSourceProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(), "true");
        ds.addDataSourceProperty(PGProperty.APPLICATION_NAME.getName(), "CockroachDB JDBC Driver Test (pooled)");
        ds.addDataSourceProperty(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getName(), "false");
        ds.addDataSourceProperty(CockroachProperty.RETRY_TRANSIENT_ERRORS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.RETRY_CONNECTION_ERRORS.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "100"); // Set high for testing
        ds.addDataSourceProperty(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "15s");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCHED_UPDATES.getName(), "true");
        ds.addDataSourceProperty(CockroachProperty.REWRITE_BATCHED_UPSERTS.getName(), "true");

        return ds;
    }

    @Bean
    @Profile("ds-pgsimple")
    @ConfigurationProperties("spring.datasource.hikari")
    public PGSimpleDataSource pgSimpleDataSource() {
        return dataSourceProperties()
                .initializeDataSourceBuilder()
                .type(PGSimpleDataSource.class)
                .build();
    }

    @Primary
    @Bean
    public DataSource dataSource() {
        DataSource dataSource;
        if (environment.acceptsProfiles(Profiles.of("ds-hikari"))) {
            dataSource = hikariDataSource();
        } else if (environment.acceptsProfiles(Profiles.of("ds-crdb"))) {
            dataSource = cockroachDataSource();
        } else if (environment.acceptsProfiles(Profiles.of("ds-pgsimple"))) {
            dataSource = pgSimpleDataSource();
        } else {
            throw new ApplicationContextException("Unrecognized datasource profile");
        }
        return ProxyDataSourceBuilder
                .create(dataSource)
                .logQueryBySlf4j(SLF4JLogLevel.TRACE, "io.cockroachdb.jdbc.SQL_TRACE")
                .asJson()
                .multiline()
                .build();
    }

    @Bean
    public LoggingRetryListener loggingRetryListener() {
        return new LoggingRetryListener();
    }
}
