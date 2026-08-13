package api;

import app.BackendApplication;
import model.AbstractUser;
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
 * Covers what CREATE_CHAT/RENAME_CHAT/ADD_USER_TO_CHAT/REMOVE_USER_FROM_CHAT used to over the
 * socket protocol (SRV-15..22 in the old ServerProtocolTest) -- HTTP-specific concerns only,
 * since ownership/membership business rules are already exhaustively covered by DBManagerTest.
 */
@SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatControllerTest {

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

    private AbstractUser owner;
    private AbstractUser other;
    private AbstractUser rando;

    @BeforeEach
    void setUpRestTemplate() {
        restTemplate = TestRestTemplates.create(port);
    }

    @BeforeEach
    void seedUsers() {
        owner = dbManager.writeNewUser("owner-" + System.nanoTime(), "pw", "Own", "Er", false, false);
        other = dbManager.writeNewUser("other-" + System.nanoTime(), "pw", "Oth", "Er", false, false);
        rando = dbManager.writeNewUser("rando-" + System.nanoTime(), "pw", "Ran", "Do", false, false);
    }

    private ApiSession loginAs(AbstractUser user, String password) {
        ApiSession session = new ApiSession(restTemplate);
        session.login(user.getUserName(), password);
        return session;
    }

    @Test
    void createChatIncludesOwnerAndInvitedMembers() {
        ApiSession session = loginAs(owner, "pw");

        var response = session.post("/api/chats",
                Map.of("otherUserIds", List.of(other.getId()), "roomName", "room1", "isPrivate", false),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<String> chatterIds = (List<String>) response.getBody().get("chatterIds");
        assertTrue(chatterIds.contains(owner.getId()));
        assertTrue(chatterIds.contains(other.getId()));
    }

    @Test
    void getAllChatsExcludesChatsNotAMember() {
        ApiSession ownerSession = loginAs(owner, "pw");
        ownerSession.post("/api/chats", Map.of("otherUserIds", List.of(), "roomName", "private-room", "isPrivate", true),
                Map.class);

        ApiSession randoSession = loginAs(rando, "pw");
        var response = randoSession.get("/api/chats", new ParameterizedTypeReference<List<Map<String, Object>>>() {
        });

        assertTrue(response.getBody().stream().noneMatch(c -> "private-room".equals(c.get("roomName"))));
    }

    @Test
    void ownerCanRenameChatNonOwnerCannot() {
        ApiSession ownerSession = loginAs(owner, "pw");
        var created = ownerSession.post("/api/chats",
                Map.of("otherUserIds", List.of(), "roomName", "old-name", "isPrivate", false), Map.class);
        String chatId = (String) created.getBody().get("id");

        var renamed = ownerSession.put("/api/chats/" + chatId + "/rename", Map.of("roomName", "new-name"), Map.class);
        assertEquals(HttpStatus.OK, renamed.getStatusCode());
        assertEquals("new-name", renamed.getBody().get("roomName"));

        ApiSession randoSession = loginAs(rando, "pw");
        var rejected = randoSession.put("/api/chats/" + chatId + "/rename", Map.of("roomName", "hijacked"), Map.class);
        assertFalse(rejected.getStatusCode().is2xxSuccessful());
    }

    @Test
    void ownerCanAddAndRemoveMembers() {
        ApiSession ownerSession = loginAs(owner, "pw");
        var created = ownerSession.post("/api/chats",
                Map.of("otherUserIds", List.of(), "roomName", "solo", "isPrivate", false), Map.class);
        String chatId = (String) created.getBody().get("id");

        var added = ownerSession.post("/api/chats/" + chatId + "/members", Map.of("userId", other.getId()), Map.class);
        assertEquals(HttpStatus.OK, added.getStatusCode());
        @SuppressWarnings("unchecked")
        List<String> afterAdd = (List<String>) added.getBody().get("chatterIds");
        assertTrue(afterAdd.contains(other.getId()));

        var removed = ownerSession.delete("/api/chats/" + chatId + "/members/" + other.getId(), Void.class);
        assertEquals(HttpStatus.OK, removed.getStatusCode());
    }

    @Test
    void nonOwnerNonAdminCannotAddMembers() {
        ApiSession ownerSession = loginAs(owner, "pw");
        var created = ownerSession.post("/api/chats",
                Map.of("otherUserIds", List.of(), "roomName", "guarded", "isPrivate", false), Map.class);
        String chatId = (String) created.getBody().get("id");

        ApiSession randoSession = loginAs(rando, "pw");
        var response = randoSession.post("/api/chats/" + chatId + "/members", Map.of("userId", other.getId()), Map.class);

        assertFalse(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void ownerCanDeleteChatNonOwnerCannot() {
        ApiSession ownerSession = loginAs(owner, "pw");
        var created = ownerSession.post("/api/chats",
                Map.of("otherUserIds", List.of(), "roomName", "to-delete", "isPrivate", false), Map.class);
        String chatId = (String) created.getBody().get("id");

        ApiSession randoSession = loginAs(rando, "pw");
        var rejected = randoSession.delete("/api/chats/" + chatId, Void.class);
        assertFalse(rejected.getStatusCode().is2xxSuccessful());

        var deleted = ownerSession.delete("/api/chats/" + chatId, Void.class);
        assertEquals(HttpStatus.OK, deleted.getStatusCode());

        assertNull(dbManager.getChatById(chatId), "chat must be gone after deletion");
    }
}
