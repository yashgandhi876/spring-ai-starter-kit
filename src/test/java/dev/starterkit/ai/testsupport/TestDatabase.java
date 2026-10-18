package dev.starterkit.ai.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A real Postgres for tests, started once per test run from bundled binaries (no Docker), with the
 * app's Flyway migrations applied. Tests clean the tables they use.
 */
public final class TestDatabase {

    private static DataSource dataSource;

    private TestDatabase() {}

    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            try {
                EmbeddedPostgres postgres = EmbeddedPostgres.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        postgres.close();
                    } catch (IOException ignored) {
                        // the JVM is exiting anyway
                    }
                }));
                dataSource = postgres.getPostgresDatabase();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Flyway.configure().dataSource(dataSource).load().migrate();
        }
        return dataSource;
    }

    public static void truncate(String... tables) {
        new JdbcTemplate(dataSource()).execute("TRUNCATE " + String.join(", ", tables));
    }
}
