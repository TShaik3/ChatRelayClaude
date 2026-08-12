package server.support;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Builds the pooled DataSource ServerMain's standalone socket server connects with. */
public final class DataSources {

    private DataSources() {
    }

    public static HikariDataSource devDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env("CHATRELAY_DB_URL", "jdbc:postgresql://localhost:5432/chatrelay_dev"));
        config.setUsername(env("CHATRELAY_DB_USER", "chatrelay"));
        config.setPassword(env("CHATRELAY_DB_PASSWORD", "chatrelay"));
        return new HikariDataSource(config);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }
}
