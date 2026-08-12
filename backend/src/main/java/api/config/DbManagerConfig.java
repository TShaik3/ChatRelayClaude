package api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import server.DBManager;

import javax.sql.DataSource;

/**
 * DBManager stays a plain, framework-agnostic class (it's also constructed manually by the
 * still-running socket Server -- see Migration Plan Phase 5 for when that goes away), so it's
 * wired into the Spring context here rather than annotated directly.
 */
@Configuration
public class DbManagerConfig {

    @Bean
    public DBManager dbManager(DataSource dataSource) {
        return new DBManager(dataSource);
    }
}
