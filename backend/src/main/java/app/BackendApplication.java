package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the new REST/WebSocket API (Migration Plan Phase 3), living in the
 * sibling `api` package -- scanned explicitly since it isn't a sub-package of `app` and Spring
 * Boot's default component scan only covers the annotated class's own package tree. The socket
 * server (ServerMain) and Swing client (ClientMain) still run independently against the same
 * Postgres database; both are retired only at cutover (Phase 5), once the Svelte frontend
 * (Phase 4) can replace them.
 */
@SpringBootApplication(scanBasePackages = {"app", "api"})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
