package server;

import model.AbstractUser;
import org.junit.jupiter.api.Test;
import packet.ActionType;
import packet.Status;
import support.TestConnection;
import support.TestDatabase;
import support.TestServerHarness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static support.TestConnection.args;

/**
 * Persistence and restart tests (TEST_PLAN.md section 8). Each "restart" is a second
 * TestServerHarness pointed at the same TestDatabase schema after the first has been closed --
 * as close to a real process restart as an in-process JUnit test can get, now that Postgres
 * rather than a flat file is what actually needs to survive the restart.
 *
 * Two cases from the flat-file era are gone rather than adapted: a chat referencing a chatter id
 * that doesn't exist (PER-4) is now impossible by construction, since chat_members.user_id is a
 * foreign key; and a file-format shape check (PER-5) has no equivalent once there's no file.
 */
class PersistenceRestartTest {

    // PER-1
    @Test
    void freshDbSeedsDefaultAdminExactlyOnce() throws Exception {
        try (TestDatabase testDb = TestDatabase.createSchema();
             TestServerHarness harness = new TestServerHarness(testDb.dataSource())) {
            assertEquals(1, harness.server().getDBManager().fetchAllUsers().size());
            assertTrue(harness.server().getDBManager().getUserByUsername("admin") != null);
        }
    }

    // PER-2 / PER-3
    @Test
    void restartPreservesDataWithoutReseedingAndAvoidsIdCollisions() throws Exception {
        try (TestDatabase testDb = TestDatabase.createSchema()) {
            String createdChatId;
            String createdUserId;
            try (TestServerHarness first = new TestServerHarness(testDb.dataSource())) {
                TestConnection admin = first.connect();
                String adminId = admin.login("admin", "admin");

                admin.send(Status.NONE, ActionType.CREATE_USER, args("persisted", "pw", "P", "P", "false", "false"), adminId);
                createdUserId = admin.recv().getActionArguments().get(0).split("/")[0];

                admin.send(Status.NONE, ActionType.CREATE_CHAT, args(createdUserId, "durable-room", "false"), adminId);
                createdChatId = admin.recv().getActionArguments().get(0).split("/")[0];

                admin.send(Status.NONE, ActionType.SEND_MESSAGE, args("still here after restart?", createdChatId), adminId);
                admin.recv();
            }

            try (TestServerHarness restarted = new TestServerHarness(testDb.dataSource())) {
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
    }

    private void assertChatStillPresentAfterRestart(TestServerHarness harness, String adminId, String expectedChatId) {
        AbstractUser admin = harness.server().getDBManager().getUserById(adminId);
        List<String> chats = harness.server().getDBManager().fetchAllChats(admin);
        assertTrue(chats.stream().anyMatch(c -> c.startsWith(expectedChatId + "/")),
                "chat created before restart must still be present after restart");
    }
}
