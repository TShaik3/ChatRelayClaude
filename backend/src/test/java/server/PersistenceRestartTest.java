package server;

import model.AbstractUser;
import model.Chat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import packet.ActionType;
import packet.Status;
import support.TestConnection;
import support.TestServerHarness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static support.TestConnection.args;

/**
 * Persistence and restart tests (TEST_PLAN.md section 8). Each "restart"
 * is a second TestServerHarness pointed at the same temp DB directory
 * after the first has been closed -- as close to a real process restart
 * as an in-process JUnit test can get.
 */
class PersistenceRestartTest {

    // PER-1
    @Test
    void freshDbSeedsDefaultAdminExactlyOnce(@TempDir Path dbDir) throws Exception {
        try (TestServerHarness harness = new TestServerHarness(dbDir)) {
            assertEquals(1, harness.server().getDBManager().fetchAllUsers().size());
            assertTrue(harness.server().getDBManager().getUserByUsername("admin") != null);
        }
    }

    // PER-2 / PER-3
    @Test
    void restartPreservesDataWithoutReseedingAndAvoidsIdCollisions(@TempDir Path dbDir) throws Exception {
        String createdChatId;
        String createdUserId;
        try (TestServerHarness first = new TestServerHarness(dbDir)) {
            TestConnection admin = first.connect();
            String adminId = admin.login("admin", "admin");

            admin.send(Status.NONE, ActionType.CREATE_USER, args("persisted", "pw", "P", "P", "false", "false"), adminId);
            createdUserId = admin.recv().getActionArguments().get(0).split("/")[0];

            admin.send(Status.NONE, ActionType.CREATE_CHAT, args(createdUserId, "durable-room", "false"), adminId);
            createdChatId = admin.recv().getActionArguments().get(0).split("/")[0];

            admin.send(Status.NONE, ActionType.SEND_MESSAGE, args("still here after restart?", createdChatId), adminId);
            admin.recv();
        }

        try (TestServerHarness restarted = new TestServerHarness(dbDir)) {
            // PER-2: admin seed must not be duplicated.
            long adminCount = restarted.server().getDBManager().fetchAllUsers().stream()
                    .filter(u -> u.contains("/admin/"))
                    .count();
            assertEquals(1, adminCount, "restart must not re-seed a duplicate admin account");

            TestConnection admin = restarted.connect();
            String adminId = admin.login("admin", "admin");
            assertChatStillPresentAfterRestart(restarted, adminId, createdChatId);

            // PER-3: a new user/chat created after restart must not collide with pre-restart ids.
            admin.send(Status.NONE, ActionType.CREATE_USER, args("afterRestart", "pw", "A", "R", "false", "false"), adminId);
            String newUserId = admin.recv().getActionArguments().get(0).split("/")[0];
            assertFalse(newUserId.equals(createdUserId));
        }
    }

    private void assertChatStillPresentAfterRestart(TestServerHarness harness, String adminId, String expectedChatId) {
        AbstractUser admin = harness.server().getDBManager().getUserById(adminId);
        List<String> chats = harness.server().getDBManager().fetchAllChats(admin);
        assertTrue(chats.stream().anyMatch(c -> c.startsWith(expectedChatId + "/")),
                "chat created before restart must still be present after restart");
    }

    // PER-4
    @Test
    void danglingChatterIdInChatsFileIsToleratedNotFatal(@TempDir Path dbDir) throws Exception {
        Files.writeString(dbDir.resolve("Users.txt"), "admin/admin/0/Admin/User/false/true\n");
        Files.writeString(dbDir.resolve("Chats.txt"), "0/0/room/false/0,999999\n");

        // Must load without throwing despite chatterId 999999 not existing in Users.txt.
        DBManager db = new DBManager(dbDir.toString(), "Users.txt", "Chats.txt", "Messages.txt");
        Chat chat = db.getChatById("0");

        assertTrue(chat.getChattersIds().contains("0"));
        assertFalse(chat.getChattersIds().contains("999999"),
                "a chatter id with no matching user must be dropped, not crash the load");
    }

    // PER-5
    @Test
    void fileFormatsMatchDocumentedShapeAfterAMixedSession(@TempDir Path dbDir) throws Exception {
        try (TestServerHarness harness = new TestServerHarness(dbDir)) {
            TestConnection admin = harness.connect();
            String adminId = admin.login("admin", "admin");

            admin.send(Status.NONE, ActionType.CREATE_USER, args("format-check", "pw", "F", "C", "false", "false"), adminId);
            String userId = admin.recv().getActionArguments().get(0).split("/")[0];

            admin.send(Status.NONE, ActionType.CREATE_CHAT, args(userId, "room-one", "false"), adminId);
            String chatId = admin.recv().getActionArguments().get(0).split("/")[0];

            admin.send(Status.NONE, ActionType.RENAME_CHAT, args(chatId, "room-renamed"), adminId);
            admin.recv();

            admin.send(Status.NONE, ActionType.SEND_MESSAGE, args("a message", chatId), adminId);
            admin.recv();

            for (String line : Files.readAllLines(dbDir.resolve("Users.txt"))) {
                assertEquals(7, line.split("/").length, "Users.txt line must be username/password/id/firstName/lastName/isDisabled/isAdmin: " + line);
            }
            for (String line : Files.readAllLines(dbDir.resolve("Chats.txt"))) {
                assertEquals(5, line.split("/").length, "Chats.txt line must be id/ownerId/roomName/isPrivate/chatterIds: " + line);
            }
            for (String line : Files.readAllLines(dbDir.resolve("Messages.txt"))) {
                assertEquals(5, line.split("/").length, "Messages.txt line must be id/createdAt/content/authorId/chatId: " + line);
            }
        }
    }
}
