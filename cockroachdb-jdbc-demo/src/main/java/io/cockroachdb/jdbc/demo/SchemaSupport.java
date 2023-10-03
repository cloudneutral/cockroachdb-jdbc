package io.cockroachdb.jdbc.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.sql.Statement;

import javax.sql.DataSource;

public abstract class SchemaSupport {
    public static final String DB_CREATE_SQL = "/db/create.sql";

    private SchemaSupport() {
    }

    public static void setupSchema(DataSource ds) throws Exception {
        InputStream is = JdbcDriverDemo.class.getResourceAsStream(DB_CREATE_SQL);
        if (is == null) {
            throw new IOException("DDL file not found: " + DB_CREATE_SQL);
        }
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(is));

        StringBuilder buffer = new StringBuilder();
        String line = reader.readLine();
        while (line != null) {
            if (!line.startsWith("--") && !line.isEmpty()) {
                buffer.append(line);
            }
            if (line.endsWith(";") && buffer.length() > 0) {
                JdbcUtils.executeWithoutTransaction(ds, conn -> {
                    try (Statement statement = conn.createStatement()) {
                        statement.execute(buffer.toString());
                    }
                    buffer.setLength(0);
                    return null;
                });
            }
            line = reader.readLine();
        }
    }
}
