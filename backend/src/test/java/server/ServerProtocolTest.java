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

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static support.TestConnection.args;

/**
 * Protocol-level integration tests: real sockets, real Server, real
 * DBManager against an isolated Postgres schema per test. IDs in comments
 * refer to TEST_PLAN.md section 6.
 */
class ServerProtocolTest {

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

    // ---- 6.1 Login / session ----

    // SRV-1
    @Test
    void loginSucceedsAndIsFollowedByFreshDataDump() throws Exception {
        TestConnection conn = harness.connect();
        conn.send(Status.NONE, ActionType.LOGIN, args("admin", "admin"), null);

        Packet loginReply = conn.recv();
        assertEquals(Status.SUCCESS, loginReply.getStatus());
        assertEquals(ActionType.LOGIN, loginReply.getActionType());
        assertEquals(5, loginReply.getActionArguments().size());
        assertEquals("true", loginReply.getActionArguments().get(3)); // isAdmin

        assertEquals(ActionType.GET_ALL_USERS, conn.recv().getActionType());
        assertEquals(ActionType.GET_ALL_CHATS, conn.recv().getActionType());
        assertEquals(ActionType.GET_ALL_MESSAGES, conn.recv().getActionType());
    }

    // SRV-2
    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        TestConnection conn = harness.connect();
        conn.send(Status.NONE, ActionType.LOGIN, args("admin", "wrong-password"), null);

        Packet reply = conn.recv();
        assertEquals(Status.ERROR, reply.getStatus());
        assertEquals(ActionType.ERROR, reply.getActionType());
    }

    // SRV-3
    @Test
    void loginForDisabledUserIsRejectedWithDistinctMessage() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER,
                args("disableme", "pw", "D", "D", "false", "false"), adminId);
        admin.recv(); // NEW_USER_BROADCAST
        admin.send(Status.NONE, ActionType.UPDATE_USER, args(
                harness.server().getDBManager().getUserByUsername("disableme").getId(),
                "disableme", "", "D", "D", "true", "false"), adminId);
        admin.recv(); // UPDATED_USER_BROADCAST

        TestConnection victim = harness.connect();
        victim.send(Status.NONE, ActionType.LOGIN, args("disableme", "pw"), null);
        Packet reply = victim.recv();

        assertEquals(Status.ERROR, reply.getStatus());
        assertTrue(reply.getActionArguments().get(0).toLowerCase().contains("disabled"));
    }

    // SRV-4
    @Test
    void loginForUnknownUsernameIsRejected() throws Exception {
        TestConnection conn = harness.connect();
        conn.send(Status.NONE, ActionType.LOGIN, args("nobody-by-this-name", "pw"), null);
        assertEquals(Status.ERROR, conn.recv().getStatus());
    }

    // SRV-5 -- documents actual current behavior: Server.clients is keyed by userId, so a
    // second concurrent login as the same account evicts the first connection's registry entry.
    // From then on, broadcasts intended for that user reach only the newest connection.
    @Test
    void secondLoginAsSameUserEvictsFirstConnectionFromBroadcastRouting() throws Exception {
        TestConnection first = harness.connect();
        String userId = first.login("admin", "admin");

        TestConnection second = harness.connect();
        String secondUserId = second.login("admin", "admin");
        assertEquals(userId, secondUserId); // same account => same id

        second.send(Status.NONE, ActionType.CREATE_USER, args("afterTakeover", "pw", "A", "T", "false", "false"), userId);
        Packet toSecond = second.recv();
        assertEquals(ActionType.NEW_USER_BROADCAST, toSecond.getActionType());

        first.setReadTimeoutMs(300);
        assertThrows(SocketTimeoutException.class, first::recv,
                "the first, now-evicted connection should no longer receive broadcasts for this userId");
    }

    // SRV-6
    @Test
    void logoutRemovesClientFromRegistry() throws Exception {
        TestConnection conn = harness.connect();
        String userId = conn.login("admin", "admin");
        assertTrue(harness.server().containsClient(userId));

        conn.send(Status.NONE, ActionType.LOGOUT, args(), userId);
        Thread.sleep(200); // give the handler thread a moment to process and exit

        assertFalse(harness.server().containsClient(userId));
    }

    // SRV-6b: logout deauthenticates the connection but must not close the socket -- the same
    // client process (one Client/GUI, one live connection) needs to be able to log back in
    // without reconnecting. Regression test for a bug where the server closed the socket on
    // LOGOUT, so the client's very next write (e.g. a second click) failed with "Broken pipe".
    @Test
    void logoutKeepsConnectionAliveForRelogin() throws Exception {
        TestConnection conn = harness.connect();
        conn.login("admin", "admin");

        conn.send(Status.NONE, ActionType.LOGOUT, args(), null);
        Thread.sleep(200);

        // Must not throw -- the same TestConnection, same underlying socket, logs back in.
        String userId = conn.login("admin", "admin");
        assertTrue(harness.server().containsClient(userId));

        // And a redundant second LOGOUT sent afterward must not blow up either.
        conn.send(Status.NONE, ActionType.LOGOUT, args(), userId);
        Thread.sleep(200);
        conn.login("admin", "admin");
    }

    // SRV-7 (also exercises the ClientHandler fix made for this case)
    @Test
    void actionBeforeLoginIsRejectedGracefullyNotWithDisconnect() throws Exception {
        TestConnection conn = harness.connect();
        conn.send(Status.NONE, ActionType.GET_ALL_USERS, args(), null);

        Packet reply = conn.recv(); // must not throw/timeout/disconnect
        assertEquals(Status.ERROR, reply.getStatus());
    }

    // SRV-8
    @Test
    void abruptDisconnectIsHandledWithoutAffectingOtherClients() throws Exception {
        TestConnection toDrop = harness.connect();
        toDrop.login("admin", "admin");
        toDrop.close();
        Thread.sleep(200);

        TestConnection other = harness.connect();
        other.login("admin", "admin"); // admin logs in again from a fresh connection; must still work
    }

    // ---- 6.2 Users ----

    // SRV-9 / SRV-10 / SRV-11
    @Test
    void createUserBroadcastsToEveryConnectedClientAdminOnly() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("bystander", "pw", "By", "Stander", "false", "false"), adminId);
        admin.recv(); // NEW_USER_BROADCAST for bystander's own creation
        TestConnection bystander = harness.connect();
        bystander.login("bystander", "pw"); // distinct account, so it gets its own slot in Server.clients

        admin.send(Status.NONE, ActionType.CREATE_USER, args("newperson", "pw", "New", "Person", "false", "false"), adminId);

        Packet toAdmin = admin.recv();
        Packet toBystander = bystander.recv();
        assertEquals(ActionType.NEW_USER_BROADCAST, toAdmin.getActionType());
        assertEquals(ActionType.NEW_USER_BROADCAST, toBystander.getActionType());

        // SRV-10 / SRV-11
        admin.send(Status.NONE, ActionType.CREATE_USER, args("newperson", "pw2", "Dup", "Licate", "false", "false"), adminId);
        assertEquals(Status.ERROR, admin.recv().getStatus());
    }

    @Test
    void nonAdminCannotCreateUser() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("regular", "pw", "Reg", "Ular", "false", "false"), adminId);
        admin.recv(); // NEW_USER_BROADCAST

        TestConnection regular = harness.connect();
        String regularId = regular.login("regular", "pw");
        regular.send(Status.NONE, ActionType.CREATE_USER, args("sneaky", "pw", "S", "S", "false", "false"), regularId);

        assertEquals(Status.ERROR, regular.recv().getStatus());
    }

    // SRV-12 / SRV-13
    @Test
    void updateUserDisablesAndBroadcastsAdminOnly() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("toBeDisabled", "pw", "T", "B", "false", "false"), adminId);
        admin.recv();
        String targetId = harness.server().getDBManager().getUserByUsername("toBeDisabled").getId();

        admin.send(Status.NONE, ActionType.UPDATE_USER, args(targetId, "toBeDisabled", "", "T", "B", "true", "false"), adminId);
        Packet broadcast = admin.recv();
        assertEquals(ActionType.UPDATED_USER_BROADCAST, broadcast.getActionType());
        String[] updatedFields = broadcast.getActionArguments().get(0).split("/");
        assertEquals(targetId, updatedFields[0]);
        assertEquals("true", updatedFields[4]); // isDisabled

        admin.send(Status.NONE, ActionType.CREATE_USER, args("regularUser", "pw", "R", "U", "false", "false"), adminId);
        admin.recv();
        TestConnection nonAdmin = harness.connect();
        String nonAdminId = nonAdmin.login("regularUser", "pw");
        nonAdmin.send(Status.NONE, ActionType.UPDATE_USER, args(adminId, "admin", "", "Admin", "User", "true", "false"), nonAdminId);
        assertEquals(Status.ERROR, nonAdmin.recv().getStatus());
    }

    // Full-profile edit ("edit user" screen): username, name, password, and admin promotion,
    // all in one UPDATE_USER call -- and the new password must actually work on the next login.
    @Test
    void updateUserFullProfileEditTakesEffectEverywhereIncludingNextLogin() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("beforeEdit", "oldpw", "Before", "Edit", "false", "false"), adminId);
        admin.recv();
        String targetId = harness.server().getDBManager().getUserByUsername("beforeEdit").getId();

        admin.send(Status.NONE, ActionType.UPDATE_USER,
                args(targetId, "afterEdit", "newpw", "After", "Edit", "false", "true"), adminId);
        Packet broadcast = admin.recv();
        String[] fields = broadcast.getActionArguments().get(0).split("/");
        assertEquals("afterEdit", fields[1]);
        assertEquals("After", fields[2]);
        assertEquals("Edit", fields[3]);
        assertEquals("false", fields[4]); // isDisabled
        assertEquals("true", fields[5]); // isAdmin

        // old credentials no longer work, new ones do
        TestConnection loginAttempt = harness.connect();
        loginAttempt.send(Status.NONE, ActionType.LOGIN, args("beforeEdit", "oldpw"), null);
        assertEquals(Status.ERROR, loginAttempt.recv().getStatus());

        TestConnection reLogin = harness.connect();
        String reLoginId = reLogin.login("afterEdit", "newpw");
        assertEquals(targetId, reLoginId);
    }

    // SRV-14
    @Test
    void getAllUsersMidSessionReturnsFreshSnapshot() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");

        admin.send(Status.NONE, ActionType.GET_ALL_USERS, args(), adminId);
        Packet reply = admin.recv();
        assertEquals(Status.SUCCESS, reply.getStatus());
        assertEquals(ActionType.GET_ALL_USERS, reply.getActionType());
    }

    // ---- 6.3 Chats ----

    // SRV-15
    @Test
    void createChatBroadcastsOnlyToInvitedMembers() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("invited", "pw", "I", "N", "false", "false"), adminId);
        admin.recv();
        admin.send(Status.NONE, ActionType.CREATE_USER, args("uninvolved", "pw", "U", "N", "false", "false"), adminId);
        admin.recv();

        TestConnection invited = harness.connect();
        invited.login("invited", "pw");
        TestConnection uninvolved = harness.connect();
        uninvolved.login("uninvolved", "pw");

        String invitedId = harness.server().getDBManager().getUserByUsername("invited").getId();
        admin.send(Status.NONE, ActionType.CREATE_CHAT, args(invitedId, "duo", "false"), adminId);

        assertEquals(ActionType.NEW_CHAT_BROADCAST, admin.recv().getActionType());
        assertEquals(ActionType.NEW_CHAT_BROADCAST, invited.recv().getActionType());

        uninvolved.setReadTimeoutMs(300);
        assertThrows(SocketTimeoutException.class, uninvolved::recv,
                "an uninvolved client must not receive this chat's broadcast");
    }

    // SRV-17 / SRV-18
    @Test
    void addUserToChatByOwnerBroadcastsAndSendsBackfillToNewMember() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_CHAT, args("", "solo-chat", "false"), adminId);
        Packet chatBroadcast = admin.recv();
        String chatId = chatBroadcast.getActionArguments().get(0).split("/")[0];

        admin.send(Status.NONE, ActionType.SEND_MESSAGE, args("before you joined", chatId), adminId);
        admin.recv(); // NEW_MESSAGE_BROADCAST to self

        admin.send(Status.NONE, ActionType.CREATE_USER, args("joiner", "pw", "J", "O", "false", "false"), adminId);
        admin.recv();
        TestConnection joiner = harness.connect();
        String joinerId = joiner.login("joiner", "pw");

        admin.send(Status.NONE, ActionType.ADD_USER_TO_CHAT, args(joinerId, chatId), adminId);
        assertEquals(ActionType.ADD_USER_TO_CHAT_BROADCAST, admin.recv().getActionType());
        assertEquals(ActionType.ADD_USER_TO_CHAT_BROADCAST, joiner.recv().getActionType());
        // backfill: joiner also gets the chat's message history scoped to their now-current chats
        assertEquals(ActionType.GET_ALL_MESSAGES, joiner.recv().getActionType());

        // SRV-18: a non-owner, non-admin chatter cannot add anyone
        admin.send(Status.NONE, ActionType.CREATE_USER, args("rando", "pw", "R", "A", "false", "false"), adminId);
        admin.recv();
        TestConnection rando = harness.connect();
        String randoId = rando.login("rando", "pw");
        rando.send(Status.NONE, ActionType.ADD_USER_TO_CHAT, args(randoId, chatId), randoId);
        assertEquals(Status.ERROR, rando.recv().getStatus());
    }

    // SRV-19
    @Test
    void removeUserFromChatReachesRemovedUserAndRemainingMembers() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("leaver", "pw", "L", "E", "false", "false"), adminId);
        admin.recv();
        String leaverId = harness.server().getDBManager().getUserByUsername("leaver").getId();
        TestConnection leaver = harness.connect();
        leaver.login("leaver", "pw");

        admin.send(Status.NONE, ActionType.CREATE_CHAT, args(leaverId, "trio", "false"), adminId);
        String chatId = admin.recv().getActionArguments().get(0).split("/")[0];
        leaver.recv(); // NEW_CHAT_BROADCAST to leaver too

        admin.send(Status.NONE, ActionType.REMOVE_USER_FROM_CHAT, args(leaverId, chatId), adminId);
        Packet toAdmin = admin.recv();
        Packet toLeaver = leaver.recv();
        assertEquals(ActionType.REMOVE_USER_FROM_CHAT_BROADCAST, toAdmin.getActionType());
        assertEquals(ActionType.REMOVE_USER_FROM_CHAT_BROADCAST, toLeaver.getActionType());
    }

    // SRV-20 / SRV-21
    @Test
    void renameChatByOwnerBroadcastsNonOwnerRejected() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_CHAT, args("", "old-name", "false"), adminId);
        String chatId = admin.recv().getActionArguments().get(0).split("/")[0];

        admin.send(Status.NONE, ActionType.RENAME_CHAT, args(chatId, "new-name"), adminId);
        Packet broadcast = admin.recv();
        assertEquals(ActionType.RENAME_CHAT_BROADCAST, broadcast.getActionType());
        assertEquals("new-name", broadcast.getActionArguments().get(1));

        admin.send(Status.NONE, ActionType.CREATE_USER, args("outsider", "pw", "O", "U", "false", "false"), adminId);
        admin.recv();
        TestConnection outsider = harness.connect();
        String outsiderId = outsider.login("outsider", "pw");
        outsider.send(Status.NONE, ActionType.RENAME_CHAT, args(chatId, "hijacked"), outsiderId);
        assertEquals(Status.ERROR, outsider.recv().getStatus());
    }

    // SRV-22
    @Test
    void getAllChatsForUserInNoChatsIsEmptySuccess() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("chatless", "pw", "C", "L", "false", "false"), adminId);
        admin.recv();
        TestConnection chatless = harness.connect();
        String chatlessId = chatless.login("chatless", "pw");

        chatless.send(Status.NONE, ActionType.GET_ALL_CHATS, args(), chatlessId);
        Packet reply = chatless.recv();
        assertEquals(Status.SUCCESS, reply.getStatus());
        assertTrue(reply.getActionArguments().isEmpty());
    }

    // ---- 6.4 Messages ----

    // SRV-23
    @Test
    void sendMessageBroadcastsToEveryChatter() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("chatmate", "pw", "C", "M", "false", "false"), adminId);
        admin.recv();
        String mateId = harness.server().getDBManager().getUserByUsername("chatmate").getId();
        TestConnection mate = harness.connect();
        mate.login("chatmate", "pw");

        admin.send(Status.NONE, ActionType.CREATE_CHAT, args(mateId, "pair", "false"), adminId);
        String chatId = admin.recv().getActionArguments().get(0).split("/")[0];
        mate.recv(); // NEW_CHAT_BROADCAST

        admin.send(Status.NONE, ActionType.SEND_MESSAGE, args("hello there", chatId), adminId);
        assertEquals(ActionType.NEW_MESSAGE_BROADCAST, admin.recv().getActionType());
        assertEquals(ActionType.NEW_MESSAGE_BROADCAST, mate.recv().getActionType());
    }

    // SRV-24 (the membership check added to DBManager.writeNewMessage)
    @Test
    void sendMessageToChatSenderIsNotAMemberOfIsRejected() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_CHAT, args("", "private-to-admin", "false"), adminId);
        String chatId = admin.recv().getActionArguments().get(0).split("/")[0];

        admin.send(Status.NONE, ActionType.CREATE_USER, args("outsider2", "pw", "O", "U", "false", "false"), adminId);
        admin.recv();
        TestConnection outsider = harness.connect();
        String outsiderId = outsider.login("outsider2", "pw");

        outsider.send(Status.NONE, ActionType.SEND_MESSAGE, args("i shouldn't be able to send this", chatId), outsiderId);
        assertEquals(Status.ERROR, outsider.recv().getStatus());
    }

    // SRV-25
    @Test
    void messageContentWithSlashesRoundTripsExactly() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_CHAT, args("", "slash-test", "false"), adminId);
        String chatId = admin.recv().getActionArguments().get(0).split("/")[0];

        String original = "a/b/c/d and 50% off!";
        admin.send(Status.NONE, ActionType.SEND_MESSAGE, args(original, chatId), adminId);
        Packet broadcast = admin.recv();
        String wireContent = broadcast.getActionArguments().get(0).split("/")[2];
        assertEquals(original, Packet.unsanitize(wireContent));
    }

    // SRV-26
    @Test
    void sendMessageToNonExistentChatIsRejected() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.SEND_MESSAGE, args("hi", "no-such-chat-id"), adminId);
        assertEquals(Status.ERROR, admin.recv().getStatus());
    }

    // SRV-27
    @Test
    void longMessageContentIsDeliveredIntact() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_CHAT, args("", "long-msg", "false"), adminId);
        String chatId = admin.recv().getActionArguments().get(0).split("/")[0];

        String longContent = "x".repeat(10_000);
        admin.send(Status.NONE, ActionType.SEND_MESSAGE, args(longContent, chatId), adminId);
        Packet broadcast = admin.recv();
        String wireContent = broadcast.getActionArguments().get(0).split("/")[2];
        assertEquals(longContent, wireContent);
    }

    // Admin moderation visibility: an IT admin's own GET_ALL_CHATS/GET_ALL_MESSAGES must include
    // chats they are not a member of, but SEND_MESSAGE into one must still be rejected -- viewing
    // for moderation is not the same capability as posting as if they belonged to it.
    @Test
    void adminSeesButCannotPostIntoChatsTheyAreNotMemberOf() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("modUserB", "pw", "B", "B", "false", "false"), adminId);
        admin.recv();
        admin.send(Status.NONE, ActionType.CREATE_USER, args("modUserC", "pw", "C", "C", "false", "false"), adminId);
        admin.recv();
        String userBId = harness.server().getDBManager().getUserByUsername("modUserB").getId();
        String userCId = harness.server().getDBManager().getUserByUsername("modUserC").getId();

        TestConnection userB = harness.connect();
        userB.login("modUserB", "pw");
        userB.send(Status.NONE, ActionType.CREATE_CHAT, args(userCId, "b-and-c-chat", "true"), userBId);
        String chatId = userB.recv().getActionArguments().get(0).split("/")[0];

        // Re-request GET_ALL_CHATS on the *same* already-logged-in admin connection (logging in a
        // second time as the same account would evict this connection from broadcast routing --
        // see SRV-5 -- and it isn't needed here anyway since a mid-session refresh proves the point).
        admin.send(Status.NONE, ActionType.GET_ALL_CHATS, args(), adminId);
        Packet chatsReply = admin.recv();
        boolean sawIt = chatsReply.getActionArguments().stream().anyMatch(c -> c.startsWith(chatId + "/"));
        assertTrue(sawIt, "admin's own GET_ALL_CHATS must include a chat they never joined");

        // But posting into it as admin is still rejected: viewing != membership.
        admin.send(Status.NONE, ActionType.SEND_MESSAGE, args("i'm just moderating, honest", chatId), adminId);
        assertEquals(Status.ERROR, admin.recv().getStatus());
    }
}
