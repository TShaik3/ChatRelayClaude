package support;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One Postgres container, shared across the entire test JVM (the standard Testcontainers
 * "singleton container" pattern -- starting a fresh container per test class would dominate the
 * run time for no isolation benefit, since TestDatabase already gets per-test isolation cheaply
 * via a fresh schema inside this one container). Started lazily on first use, left running for
 * the JVM to reap on exit (Testcontainers' Ryuk resource reaper handles cleanup even if the JVM
 * is killed).
 */
final class TestPostgresContainer {

    private static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("chatrelay_test")
                    .withUsername("chatrelay")
                    .withPassword("chatrelay");

    static {
        INSTANCE.start();
    }

    private TestPostgresContainer() {
    }

    static String jdbcUrl() {
        return "jdbc:postgresql://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(5432)
                + "/" + INSTANCE.getDatabaseName();
    }

    static String username() {
        return INSTANCE.getUsername();
    }

    static String password() {
        return INSTANCE.getPassword();
    }
}
