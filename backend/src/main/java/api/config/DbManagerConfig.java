package api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import server.DBManager;

import javax.sql.DataSource;

/**
 * DBManager stays a plain, framework-agnostic class (Phase 1/3 built it that way so it could also
 * be constructed manually by the old socket Server), so it's wired into the Spring context here
 * rather than annotated directly.
 */
@Configuration
public class DbManagerConfig {

    private static final Logger log = LoggerFactory.getLogger(DbManagerConfig.class);

    /**
     * DBManager's constructor queries the users/chats/messages tables immediately (to bootstrap
     * the id counters), so it must not run before Flyway has created them. Nothing makes that
     * ordering automatic for a plain @Bean method the way it does for Spring Data repositories --
     * this went unnoticed until this bean was ever built against a schema Flyway hadn't already
     * migrated in an earlier run (i.e. never, until Docker's fresh Postgres container).
     */
    @Bean
    @DependsOn("flywayInitializer")
    public DBManager dbManager(DataSource dataSource) {
        return new DBManager(dataSource);
    }

    /**
     * A brand-new deployment otherwise has no way to log in at all -- nothing else creates the
     * first account. This replaces Server.seedDefaultAdmin(), which used to do exactly this at
     * construction time; there was no equivalent in the REST app until a genuinely fresh database
     * (Docker's Postgres container, not the long-since-migrated local chatrelay_dev) exposed the gap.
     */
    @Bean
    public ApplicationRunner seedDefaultAdmin(DBManager dbManager) {
        return args -> {
            if (dbManager.listAllUsers().isEmpty()) {
                dbManager.writeNewUser("admin", "admin", "Admin", "User", false, true);
                log.info("No users found; created default admin (username=admin, password=admin)");
            }
        };
    }
}
