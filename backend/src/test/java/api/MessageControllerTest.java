package api;

import app.BackendApplication;
import model.AbstractUser;
import model.Chat;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what SEND_MESSAGE/GET_ALL_MESSAGES used to over the socket protocol (SRV-23..27 in the
 * old ServerProtocolTest), scoped per-chat as GET/POST /api/chats/{id}/messages now are.
 */
@SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessageControllerTest {

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

    private AbstractUser member;
    private AbstractUser outsider;
    private Chat chat;

    @BeforeEach
    void setUpRestTemplate() {
        restTemplate = TestRestTemplates.create(port);
    }

    @BeforeEach
    void seedFixtures() {
        member = dbManager.writeNewUser("member-" + System.nanoTime(), "pw", "Mem", "Ber", false, false);
        outsider = dbManager.writeNewUser("outsider-" + System.nanoTime(), "pw", "Out", "Sider", false, false);
        chat = dbManager.writeNewChat(member.getId(), "chat-" + System.nanoTime(), new ArrayList<>(), false);
    }

    private ApiSession loginAs(AbstractUser user) {
        ApiSession session = new ApiSession(restTemplate);
        session.login(user.getUserName(), "pw");
        return session;
    }

    @Test
    void memberCanSendAndListMessages() {
        ApiSession session = loginAs(member);

        var sent = session.post("/api/chats/" + chat.getId() + "/messages", Map.of("content", "hello there"), Map.class);
        assertEquals(HttpStatus.OK, sent.getStatusCode());
        assertEquals("hello there", sent.getBody().get("content"));

        var list = session.get("/api/chats/" + chat.getId() + "/messages",
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
        assertTrue(list.getBody().stream().anyMatch(m -> "hello there".equals(m.get("content"))));
    }

    @Test
    void nonMemberCannotSendMessage() {
        ApiSession session = loginAs(outsider);

        var response = session.post("/api/chats/" + chat.getId() + "/messages",
                Map.of("content", "i shouldn't be able to send this"), Map.class);

        assertFalse(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void nonMemberCannotListMessages() {
        ApiSession session = loginAs(outsider);

        // A rejected request comes back shaped like {"error": "..."} (see ApiExceptionHandler),
        // not a message list, so it must be read as a Map here rather than the success-shaped type.
        var response = session.get("/api/chats/" + chat.getId() + "/messages", Map.class);

        assertFalse(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void sendingToNonExistentChatIsRejected() {
        ApiSession session = loginAs(member);

        var response = session.post("/api/chats/999999/messages", Map.of("content", "hi"), Map.class);

        assertFalse(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void messageContentWithSlashesRoundTripsExactly() {
        ApiSession session = loginAs(member);
        String original = "a/b/c/d and 50% off!";

        var response = session.post("/api/chats/" + chat.getId() + "/messages", Map.of("content", original), Map.class);

        assertEquals(original, response.getBody().get("content"),
                "JSON needs no manual slash-escaping, unlike the old wire protocol's sanitize()/unsanitize()");
    }
}
