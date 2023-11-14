package io.cockroachdb.jdbc.test;

import io.cockroachdb.jdbc.CockroachDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("ds-crdb")
public class CockroachDataSourceConfiguration extends AbstractDataSourceConfiguration {
    @Override
    @Bean
    public DataSource actualDataSource() {
        CockroachDataSource ds = dataSourceProperties()
                .initializeDataSourceBuilder()
                .type(CockroachDataSource.class)
                .build();
        ds.setAutoCommit(true);
        allDataSourceProperties().forEach(ds::addDataSourceProperty);
        return ds;
    }
}
