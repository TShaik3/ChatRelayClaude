package support;

import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * A throwaway Postgres schema, migrated with the app's real Flyway scripts, standing in for the
 * old "@TempDir Path dbDir" isolation the flat-file DBManager tests used to get for free: each
 * call to createSchema() is a fresh, empty logical database within a shared Testcontainers
 * Postgres instance (see TestPostgresContainer), dropped again on close(). Point multiple
 * DBManager instances at the same TestDatabase's dataSource() to simulate a process restart
 * against un-wiped data.
 */
public class TestDatabase implements AutoCloseable {

    private static final String BASE_URL = TestPostgresContainer.jdbcUrl();
    private static final String USER = TestPostgresContainer.username();
    private static final String PASSWORD = TestPostgresContainer.password();

    private final String schemaName;
    private final DataSource dataSource;
    private final String jdbcUrl;

    private TestDatabase(String schemaName, DataSource dataSource, String jdbcUrl) {
        this.schemaName = schemaName;
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
    }

    public static TestDatabase createSchema() {
        String schemaName = "test_" + UUID.randomUUID().toString().replace("-", "");
        String url = BASE_URL + "?currentSchema=" + schemaName;

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(USER);
        ds.setPassword(PASSWORD);

        Flyway.configure()
                .dataSource(ds)
                .schemas(schemaName)
                .load()
                .migrate();

        return new TestDatabase(schemaName, ds, url);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    /** For @DynamicPropertySource in @SpringBootTest classes, which need raw connection strings. */
    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return USER;
    }

    public String password() {
        return PASSWORD;
    }

    /** Direct row count for tests that need to assert persistence without going through DBManager. */
    public long countRows(String table) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        PGSimpleDataSource adminDs = new PGSimpleDataSource();
        adminDs.setUrl(BASE_URL);
        adminDs.setUser(USER);
        adminDs.setPassword(PASSWORD);
        try (Connection conn = adminDs.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
