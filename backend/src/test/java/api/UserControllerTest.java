package api;

import app.BackendApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import server.DBManager;
import support.ApiSession;
import support.TestDatabase;
import support.TestRestTemplates;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what CREATE_USER/UPDATE_USER/GET_ALL_USERS used to over the socket protocol
 * (SRV-9..14 in the old ServerProtocolTest), focused on the HTTP-specific concerns the REST
 * rewrite introduces (status codes, the admin-only guard as @PreAuthorize instead of
 * Server.requireAdmin) -- the underlying business rules (duplicate usernames, password hashing,
 * etc.) are already exhaustively covered by DBManagerTest against the same DBManager.
 */
@SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest {

    private static final TestDatabase TEST_DB = TestDatabase.createSchema();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TEST_DB::jdbcUrl);
        registry.add("spring.datasource.username", TEST_DB::username);
        registry.add("spring.datasource.password", TEST_DB::password);
    }

    @AfterAll
    static void tearDown() {
        TEST_DB.close();
    }

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;

    @Autowired
    private DBManager dbManager;

    @BeforeEach
    void setUpRestTemplate() {
        restTemplate = TestRestTemplates.create(port);
    }

    @BeforeEach
    void seedUsers() {
        if (dbManager.getUserByUsername("admin1") == null) {
            dbManager.writeNewUser("admin1", "pw", "Ad", "Min", false, true);
        }
        if (dbManager.getUserByUsername("regular1") == null) {
            dbManager.writeNewUser("regular1", "pw", "Reg", "Ular", false, false);
        }
    }

    @Test
    void anyAuthenticatedUserCanListUsers() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("regular1", "pw");

        var response = session.get("/api/users", new ParameterizedTypeReference<List<Map<String, Object>>>() {
        });

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().stream().anyMatch(u -> "admin1".equals(u.get("username"))));
        assertTrue(response.getBody().stream().noneMatch(u -> u.containsKey("password")),
                "UserDto must never expose a password field");
    }

    @Test
    void adminCanCreateUserAndItBroadcasts() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("admin1", "pw");

        var response = session.post("/api/users",
                Map.of("username", "created1", "password", "pw", "firstName", "C", "lastName", "D",
                        "disabled", false, "admin", false),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("created1", response.getBody().get("username"));
    }

    @Test
    void nonAdminCannotCreateUser() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("regular1", "pw");

        var response = session.post("/api/users",
                Map.of("username", "sneaky1", "password", "pw", "firstName", "S", "lastName", "S",
                        "disabled", false, "admin", false),
                Map.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void adminCanUpdateAnotherUser() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("admin1", "pw");
        var target = dbManager.writeNewUser("toEdit1", "pw", "To", "Edit", false, false);

        var response = session.put("/api/users/" + target.getId(),
                Map.of("username", "toEdit1", "password", "", "firstName", "Edited", "lastName", "Edit",
                        "disabled", false, "admin", false),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Edited", response.getBody().get("firstName"));
    }

    @Test
    void nonAdminCannotUpdateAnotherUser() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("regular1", "pw");
        var target = dbManager.writeNewUser("toEdit2", "pw", "To", "Edit", false, false);

        var response = session.put("/api/users/" + target.getId(),
                Map.of("username", "hijacked", "password", "", "firstName", "H", "lastName", "H",
                        "disabled", false, "admin", true),
                Map.class);

        assertFalse(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void regularUserCanUpdateOwnProfileViaMe() {
        var self = dbManager.writeNewUser("selfEdit1", "pw", "Before", "Name", false, false);
        ApiSession session = new ApiSession(restTemplate);
        session.login("selfEdit1", "pw");

        var response = session.put("/api/users/me",
                Map.of("username", "selfEdit1", "password", "", "firstName", "After", "lastName", "Name"),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("After", response.getBody().get("firstName"));
        assertEquals("After", dbManager.getUserById(self.getId()).getFirstName());
    }

    @Test
    void updateSelfCannotEscalateOwnPrivileges() {
        var self = dbManager.writeNewUser("selfEdit2", "pw", "Regular", "User", false, false);
        ApiSession session = new ApiSession(restTemplate);
        session.login("selfEdit2", "pw");

        // UpdateSelfRequest has no admin/disabled fields, so a client sneaking them into the JSON
        // body has no path to them ever reaching DBManager.updateUserDetails.
        var response = session.put("/api/users/me",
                Map.of("username", "selfEdit2", "password", "", "firstName", "Regular", "lastName", "User",
                        "admin", true, "disabled", true),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var reloaded = dbManager.getUserById(self.getId());
        assertFalse(reloaded.isAdmin());
        assertFalse(reloaded.isDisabled());
    }

    @Test
    void adminCanDeleteAnUnusedUser() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("admin1", "pw");
        var target = dbManager.writeNewUser("toDelete1", "pw", "To", "Delete", false, false);

        var response = session.delete("/api/users/" + target.getId(), Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(dbManager.getUserById(target.getId()));
    }

    @Test
    void nonAdminCannotDeleteUser() {
        var target = dbManager.writeNewUser("toDelete2", "pw", "To", "Delete", false, false);
        ApiSession session = new ApiSession(restTemplate);
        session.login("regular1", "pw");

        var response = session.delete("/api/users/" + target.getId(), Void.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void adminCannotDeleteTheirOwnAccount() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("admin1", "pw");
        var self = dbManager.getUserByUsername("admin1");

        var response = session.delete("/api/users/" + self.getId(), Void.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
