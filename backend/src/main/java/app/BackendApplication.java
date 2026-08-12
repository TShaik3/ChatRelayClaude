package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Placeholder Spring Boot entry point for the migration in progress -- the
 * actual socket server (ServerMain) and Swing client (ClientMain) still run
 * independently until the REST/WebSocket layer replaces them (Migration
 * Plan Phase 3). application.properties now points at the real chatrelay_dev
 * Postgres database, so Spring Boot's default DataSource/Flyway
 * auto-configuration applies unmodified.
 */
@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
