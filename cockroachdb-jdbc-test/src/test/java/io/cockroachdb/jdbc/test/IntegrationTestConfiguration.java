package io.cockroachdb.jdbc.test;

import io.cockroachdb.jdbc.retry.LoggingRetryListener;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class
})
@ComponentScan(basePackageClasses = IntegrationTestConfiguration.class)
@Configuration
public class IntegrationTestConfiguration {
    @Bean
    public LoggingRetryListener loggingRetryListener() {
        return new LoggingRetryListener();
    }
}
