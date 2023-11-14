package io.cockroachdb.jdbc.test;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("ds-hikari")
public class HikariDataSourceConfiguration extends AbstractDataSourceConfiguration {
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    @Override
    public DataSource actualDataSource() {
        HikariDataSource ds = dataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.setAutoCommit(true);
        allDataSourceProperties().forEach(ds::addDataSourceProperty);
        return ds;
    }
}
