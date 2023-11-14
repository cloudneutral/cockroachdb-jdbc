package io.cockroachdb.jdbc.test;

import io.cockroachdb.jdbc.CockroachProperty;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.postgresql.PGProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractDataSourceConfiguration {
    protected static Map<String, Object> allDataSourceProperties() {
        Map<String, Object> map = new HashMap<>();
        map.put(PGProperty.APPLICATION_NAME.getName(), "CockroachDB JDBC Driver");
        map.put(PGProperty.REWRITE_BATCHED_INSERTS.getName(), "true");

        map.put(CockroachProperty.REWRITE_BATCHED_INSERTS.getName(), "true");
        map.put(CockroachProperty.REWRITE_BATCHED_UPSERTS.getName(), "true");
        map.put(CockroachProperty.REWRITE_BATCHED_UPDATES.getName(), "true");

        map.put(CockroachProperty.IMPLICIT_SELECT_FOR_UPDATE.getName(), "false");

        map.put(CockroachProperty.RETRY_TRANSIENT_ERRORS.getName(), "true");
        map.put(CockroachProperty.RETRY_CONNECTION_ERRORS.getName(), "true");
        map.put(CockroachProperty.RETRY_MAX_ATTEMPTS.getName(), "100"); // Set high for testing
        map.put(CockroachProperty.RETRY_MAX_BACKOFF_TIME.getName(), "15s");

        return map;
    }

    protected final Logger logger = LoggerFactory.getLogger("io.cockroachdb.jdbc.SQL_TRACE");

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        if (logger.isTraceEnabled()) {
            return ProxyDataSourceBuilder
                    .create(actualDataSource())
                    .logQueryBySlf4j(SLF4JLogLevel.TRACE, logger.getName())
                    .asJson()
                    .multiline()
                    .build();
        }

        return actualDataSource();
    }

    public abstract DataSource actualDataSource();
}
