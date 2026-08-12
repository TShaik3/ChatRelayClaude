package api;

import app.BackendApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import server.DBManager;
import support.ApiSession;
import support.TestDatabase;
import support.TestRestTemplates;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what the socket protocol's LOGIN/LOGOUT handling used to (ClientHandler.handleLogin,
 * SRV-2/SRV-3/SRV-4 in the old ServerProtocolTest): correct/incorrect credentials, disabled
 * accounts, unknown usernames -- now expressed as HTTP status codes and a session cookie instead
 * of Packet/Status values.
 */
@SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

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
        if (dbManager.getUserByUsername("alice") == null) {
            dbManager.writeNewUser("alice", "correct-pw", "Alice", "A", false, false);
        }
        if (dbManager.getUserByUsername("disabledUser") == null) {
            dbManager.writeNewUser("disabledUser", "pw", "D", "D", true, false);
        }
    }

    @Test
    void correctCredentialsLogInAndEstablishASession() {
        ApiSession session = new ApiSession(restTemplate);

        var response = session.login("alice", "correct-pw");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("alice", response.getBody().get("username"));
        assertNotNull(session.sessionCookie(), "login must establish a session cookie");

        // the session actually works for a subsequent request, not just the login response itself
        var me = session.get("/api/auth/me", Map.class);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals("alice", me.getBody().get("username"));
    }

    @Test
    void wrongPasswordIsRejected() {
        ApiSession session = new ApiSession(restTemplate);

        var response = session.login("alice", "wrong-password");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void unknownUsernameIsRejected() {
        ApiSession session = new ApiSession(restTemplate);

        var response = session.login("nobody-by-this-name", "pw");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void disabledAccountIsRejectedWithDistinctMessage() {
        ApiSession session = new ApiSession(restTemplate);

        var response = session.login("disabledUser", "pw");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().toLowerCase().contains("disabled"));
    }

    @Test
    void logoutInvalidatesTheSession() {
        ApiSession session = new ApiSession(restTemplate);
        session.login("alice", "correct-pw");

        session.logout();

        var response = session.get("/api/auth/me", Map.class);
        assertFalse(response.getStatusCode().is2xxSuccessful(), "session must no longer be valid after logout");
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        ApiSession session = new ApiSession(restTemplate);

        var response = session.get("/api/auth/me", Map.class);

        assertFalse(response.getStatusCode().is2xxSuccessful());
    }
}
