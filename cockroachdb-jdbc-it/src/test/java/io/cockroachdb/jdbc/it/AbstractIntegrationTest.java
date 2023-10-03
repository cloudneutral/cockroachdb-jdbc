package io.cockroachdb.jdbc.it;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.platform.commons.util.AnnotationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.StringUtils;

import io.cockroachdb.jdbc.CockroachDriver;
import io.cockroachdb.jdbc.retry.LoggingRetryListener;
import jakarta.annotation.PostConstruct;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(classes = TestConfiguration.class)
public abstract class AbstractIntegrationTest {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected LoggingRetryListener singletonRetryListener;

    @PostConstruct
    public void postConstruct() {
        CockroachDriver.setRetryListenerSupplier(() -> singletonRetryListener);
    }

    @BeforeEach
    public void beforeEachTest() {
        singletonRetryListener.resetCounters();

        AnnotationUtils.findAnnotation(getClass(), DatabaseFixture.class).ifPresent(config -> {
            if (StringUtils.hasLength(config.beforeTestScript())) {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource(config.beforeTestScript()));
                populator.setCommentPrefixes("--", "#");
                populator.setIgnoreFailedDrops(false);

                DatabasePopulatorUtils.execute(populator, dataSource);
            }
        });
    }

    @AfterEach
    public void afterEachTest() {
        singletonRetryListener.resetCounters();

        AnnotationUtils.findAnnotation(getClass(), DatabaseFixture.class).ifPresent(config -> {
            if (StringUtils.hasLength(config.afterTestScript())) {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource(config.beforeTestScript()));
                populator.setCommentPrefixes("--", "#");
                populator.setIgnoreFailedDrops(false);

                DatabasePopulatorUtils.execute(populator, dataSource);
            }
        });
    }

}
