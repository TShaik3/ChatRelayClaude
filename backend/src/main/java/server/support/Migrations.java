package server.support;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Runs the db/migration Flyway scripts against a DataSource. Called explicitly rather than
 * relying on Spring Boot's Flyway auto-configuration, since ServerMain's socket server (and the
 * test harness) construct a DataSource outside of any Spring application context -- that
 * auto-configuration only applies once the REST/WebSocket layer replaces it (Migration Plan
 * Phase 3).
 */
public final class Migrations {

    private Migrations() {
    }

    public static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();
    }
}
