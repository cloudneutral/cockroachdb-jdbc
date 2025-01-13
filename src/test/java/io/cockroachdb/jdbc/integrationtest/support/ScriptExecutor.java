package io.cockroachdb.jdbc.integrationtest.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.sql.DataSource;

import org.junit.platform.commons.util.AnnotationUtils;

public abstract class ScriptExecutor {
    private ScriptExecutor() {
    }

    public static void executeBeforeScript(Class<?> testClass, DataSource dataSource) {
        AnnotationUtils.findAnnotation(testClass, DatabaseFixture.class)
                .ifPresent(config -> {
                    if (StringUtils.hasLength(config.beforeTestScript())) {
                        JdbcTemplate template = JdbcTemplate.from(dataSource);

                        InputStream is = testClass.getResourceAsStream(config.beforeTestScript());

                        try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                            StringBuilder sql = new StringBuilder();
                            for (String line : r.lines().toList()) {
                                if (!line.startsWith("#") && !line.startsWith("--")) {
                                    sql.append(line);
                                    sql.append(" ");
                                }
                                if (line.endsWith(";")) {
                                    template.execute(sql.toString());
                                    sql.setLength(0);
                                }
                            }
                            if (!sql.isEmpty()) {
                                template.execute(sql.toString());
                            }
                        } catch (RuntimeSQLException e) {
                            throw new RuntimeException("SQL exception in " + config.beforeTestScript(), e.getCause());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

    }
}
