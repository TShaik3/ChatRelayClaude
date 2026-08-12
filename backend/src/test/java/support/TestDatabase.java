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
 * call to createSchema() is a fresh, empty logical database within the shared chatrelay_test
 * database, dropped again on close(). Point multiple DBManager/Server instances at the same
 * TestDatabase's dataSource() to simulate a process restart against un-wiped data.
 */
public class TestDatabase implements AutoCloseable {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "CHATRELAY_TEST_DB_URL", "jdbc:postgresql://localhost:5432/chatrelay_test");
    private static final String USER = System.getenv().getOrDefault("CHATRELAY_TEST_DB_USER", "chatrelay");
    private static final String PASSWORD = System.getenv().getOrDefault("CHATRELAY_TEST_DB_PASSWORD", "chatrelay");

    private final String schemaName;
    private final DataSource dataSource;

    private TestDatabase(String schemaName, DataSource dataSource) {
        this.schemaName = schemaName;
        this.dataSource = dataSource;
    }

    public static TestDatabase createSchema() {
        String schemaName = "test_" + UUID.randomUUID().toString().replace("-", "");

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(BASE_URL + "?currentSchema=" + schemaName);
        ds.setUser(USER);
        ds.setPassword(PASSWORD);

        Flyway.configure()
                .dataSource(ds)
                .schemas(schemaName)
                .load()
                .migrate();

        return new TestDatabase(schemaName, ds);
    }

    public DataSource dataSource() {
        return dataSource;
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
