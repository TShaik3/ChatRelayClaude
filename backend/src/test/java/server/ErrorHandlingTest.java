package server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import packet.ActionType;
import packet.Packet;
import packet.Status;
import support.TestConnection;
import support.TestDatabase;
import support.TestServerHarness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static support.TestConnection.args;

/**
 * Adversarial/malformed-input tests (TEST_PLAN.md section 10).
 */
class ErrorHandlingTest {

    private TestDatabase testDb;
    private TestServerHarness harness;

    @BeforeEach
    void startServer() throws Exception {
        testDb = TestDatabase.createSchema();
        harness = new TestServerHarness(testDb.dataSource());
    }

    @AfterEach
    void stopServer() {
        harness.close();
        testDb.close();
    }

    // ERR-1
    @Test
    void shortArgListOnLoginGetsCleanErrorNotDisconnect() throws Exception {
        TestConnection conn = harness.connect();
        conn.send(Status.NONE, ActionType.LOGIN, args("admin"), null); // missing password

        Packet reply = conn.recv(); // must not throw/timeout
        assertEquals(Status.ERROR, reply.getStatus());

        // connection must still be usable afterward
        conn.send(Status.NONE, ActionType.LOGIN, args("admin", "admin"), null);
        assertEquals(Status.SUCCESS, conn.recv().getStatus());
    }

    // ERR-1 (same gap, a different already-authenticated action)
    @Test
    void shortArgListOnAuthenticatedActionGetsCleanError() throws Exception {
        TestConnection conn = harness.connect();
        String userId = conn.login("admin", "admin");

        conn.send(Status.NONE, ActionType.SEND_MESSAGE, args("only one arg"), userId); // missing chatId
        Packet reply = conn.recv();
        assertEquals(Status.ERROR, reply.getStatus());

        // connection must still be usable afterward
        conn.send(Status.NONE, ActionType.GET_ALL_USERS, args(), userId);
        assertEquals(Status.SUCCESS, conn.recv().getStatus());
    }

    // ERR-2
    @Test
    void nonBooleanStringDegradesToFalseRatherThanCrashing() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");

        admin.send(Status.NONE, ActionType.CREATE_CHAT, args("", "maybe-private", "maybe"), adminId);
        Packet reply = admin.recv();

        assertEquals(ActionType.NEW_CHAT_BROADCAST, reply.getActionType());
        String isPrivateField = reply.getActionArguments().get(0).split("/")[3];
        assertEquals("false", isPrivateField, "Boolean.parseBoolean(\"maybe\") is documented to silently yield false");
    }

    // ERR-3
    @Test
    void nonExistentIdLooksLikeAnyOtherUnknownIdNoCrash() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");

        admin.send(Status.NONE, ActionType.ADD_USER_TO_CHAT, args("not-a-real-id-at-all", "also-not-real"), adminId);
        assertEquals(Status.ERROR, admin.recv().getStatus());
    }

    // ERR-4
    @Test
    void garbageBytesOnOneConnectionDoNotAffectOtherClients() throws Exception {
        TestConnection victim = harness.connect();
        victim.sendRaw("just a plain string, not a Packet"); // wrong wire type for this protocol

        // that connection's ClientHandler thread dies deserializing/casting it, but the server
        // itself, and every other connection, must be entirely unaffected.
        TestConnection healthy = harness.connect();
        healthy.login("admin", "admin");
    }

    // ERR-5
    @Test
    void actionTypeWithNoServerSideCaseErrorsGracefully() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");

        // NEW_MESSAGE_BROADCAST is a server-to-client-only type; a client sending it back
        // has no matching case in Server.receivePacket's switch and must hit the default branch.
        admin.send(Status.NONE, ActionType.NEW_MESSAGE_BROADCAST, args("x", "y"), adminId);
        assertEquals(Status.ERROR, admin.recv().getStatus());
    }
}
