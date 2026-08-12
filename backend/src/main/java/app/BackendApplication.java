package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Placeholder Spring Boot entry point for the migration in progress -- the
 * actual socket server (ServerMain) and Swing client (ClientMain) still run
 * independently until the REST/WebSocket layer replaces them (Migration
 * Plan Phase 3). DataSourceAutoConfiguration is excluded for now since no
 * datasource is configured until the Postgres migration (Phase 1); remove
 * this exclusion once application.properties points at a real database.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
