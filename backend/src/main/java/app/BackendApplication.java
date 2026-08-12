package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the REST/WebSocket API, serving both the JSON endpoints under
 * `/api` and the built Svelte frontend as static resources. Lives in the sibling `api` package --
 * scanned explicitly since it isn't a sub-package of `app` and Spring Boot's default component
 * scan only covers the annotated class's own package tree.
 */
@SpringBootApplication(scanBasePackages = {"app", "api"})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
