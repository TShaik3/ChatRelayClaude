package server;

import model.AbstractUser;
import model.Chat;
import model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DBManagerTest {

    private DBManager newManager(Path dir) {
        return new DBManager(dir.toString(), "Users.txt", "Chats.txt", "Messages.txt");
    }

    // DB-1
    @Test
    void constructingAgainstEmptyDirStartsEmptyAndCreatesDir(@TempDir Path dir) {
        Path fresh = dir.resolve("nested/does/not/exist/yet");
        DBManager db = new DBManager(fresh.toString(), "Users.txt", "Chats.txt", "Messages.txt");

        assertTrue(db.fetchAllUsers().isEmpty());
        assertTrue(Files.isDirectory(fresh));
    }

    // DB-2
    @Test
    void writeNewUserIsFindableByIdAndUsername(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser user = db.writeNewUser("alice", "pw", "Alice", "A", false, false);

        assertEquals(user, db.getUserById(user.getId()));
        assertEquals(user, db.getUserByUsername("alice"));
    }

    // DB-3
    @Test
    void writeNewUserRejectsDuplicateUsername(@TempDir Path dir) throws IOException {
        DBManager db = newManager(dir);
        db.writeNewUser("alice", "pw", "Alice", "A", false, false);

        assertThrows(IllegalArgumentException.class,
                () -> db.writeNewUser("alice", "different-pw", "Alice2", "A2", false, false));

        long lineCount = Files.lines(dir.resolve("Users.txt")).count();
        assertEquals(1, lineCount, "rejected duplicate must not be written to disk");
    }

    // DB-4 / DB-5
    @Test
    void reloadingFromDiskRestoresUsersAndAvoidsIdCollisions(@TempDir Path dir) {
        DBManager first = newManager(dir);
        AbstractUser original = first.writeNewUser("bob", "pw", "Bob", "B", false, false);

        DBManager reloaded = newManager(dir);
        AbstractUser fromDisk = reloaded.getUserById(original.getId());
        assertEquals(original.getUserName(), fromDisk.getUserName());
        assertEquals(original.getFirstName(), fromDisk.getFirstName());

        AbstractUser afterRestart = reloaded.writeNewUser("carol", "pw", "Carol", "C", false, false);
        assertNotEquals(original.getId(), afterRestart.getId());
        assertTrue(Integer.parseInt(afterRestart.getId()) > Integer.parseInt(original.getId()));
    }

    // DB-6
    @Test
    void checkLoginCredentialsRejectsWrongPassword(@TempDir Path dir) {
        DBManager db = newManager(dir);
        db.writeNewUser("dave", "correct-pw", "Dave", "D", false, false);

        assertNull(db.checkLoginCredentials("dave", "wrong-pw"));
        assertNotEquals(null, db.checkLoginCredentials("dave", "correct-pw"));
    }

    // DB-7
    @Test
    void checkLoginCredentialsDoesNotItselfRejectDisabledUsers(@TempDir Path dir) {
        // Disabled-account rejection is enforced by the server layer (see ServerProtocolTest),
        // not by DBManager itself -- pin down that boundary explicitly.
        DBManager db = newManager(dir);
        AbstractUser user = db.writeNewUser("eve", "pw", "Eve", "E", true, false);

        AbstractUser result = db.checkLoginCredentials("eve", "pw");
        assertEquals(user.getId(), result.getId());
        assertTrue(result.isDisabled());
    }

    // DB-8
    @Test
    void updateUserIsDisabledPersistsAcrossReload(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser user = db.writeNewUser("frank", "pw", "Frank", "F", false, false);
        db.updateUserIsDisabled(user.getId(), true);

        DBManager reloaded = newManager(dir);
        assertTrue(reloaded.getUserById(user.getId()).isDisabled());
    }

    @Test
    void updateUserDetailsChangesEverythingAndPersists(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser user = db.writeNewUser("original", "origpw", "Orig", "Inal", false, false);

        AbstractUser updated = db.updateUserDetails(user.getId(), "renamed", "Ren", "Amed", true, true, "newpw");

        assertEquals("renamed", updated.getUserName());
        assertEquals("Ren", updated.getFirstName());
        assertEquals("Amed", updated.getLastName());
        assertTrue(updated.isDisabled());
        assertTrue(updated.isAdmin());
        assertEquals("newpw", updated.getPassword());
        assertEquals(user.getId(), updated.getId(), "id must never change");

        DBManager reloaded = newManager(dir);
        AbstractUser fromDisk = reloaded.getUserById(user.getId());
        assertEquals("renamed", fromDisk.getUserName());
        assertEquals("newpw", fromDisk.getPassword());
        assertTrue(fromDisk.isAdmin());
    }

    @Test
    void updateUserDetailsWithBlankPasswordLeavesPasswordUnchanged(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser user = db.writeNewUser("keeppw", "keep-this-password", "K", "P", false, false);

        AbstractUser updated = db.updateUserDetails(user.getId(), "keeppw", "K2", "P2", false, false, "");

        assertEquals("keep-this-password", updated.getPassword());
    }

    @Test
    void updateUserDetailsRejectsUsernameAlreadyTakenByAnotherUser(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser userA = db.writeNewUser("userTaken", "pw", "A", "A", false, false);
        AbstractUser userB = db.writeNewUser("userFree", "pw", "B", "B", false, false);

        assertThrows(IllegalArgumentException.class,
                () -> db.updateUserDetails(userB.getId(), "userTaken", "B", "B", false, false, ""));

        // renaming a user to their OWN current username must not spuriously conflict with themself
        AbstractUser unchanged = db.updateUserDetails(userA.getId(), "userTaken", "A2", "A", false, false, "");
        assertEquals("A2", unchanged.getFirstName());
    }

    @Test
    void updateUserDetailsRejectsUnknownUser(@TempDir Path dir) {
        DBManager db = newManager(dir);
        assertThrows(IllegalArgumentException.class,
                () -> db.updateUserDetails("no-such-id", "x", "X", "X", false, false, ""));
    }

    // DB-9
    @Test
    void writeNewChatAlwaysIncludesOwnerEvenIfNotInChatterList(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("owner", "pw", "Own", "Er", false, false);
        AbstractUser other = db.writeNewUser("other", "pw", "Oth", "Er", false, false);

        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(List.of(other.getId())), false);

        assertTrue(chat.getChattersIds().contains(owner.getId()));
        assertTrue(chat.getChattersIds().contains(other.getId()));
    }

    // DB-10
    @Test
    void writeNewChatRejectsUnknownOwner(@TempDir Path dir) {
        DBManager db = newManager(dir);
        assertThrows(IllegalArgumentException.class,
                () -> db.writeNewChat("no-such-user", "room", new ArrayList<>(), false));
    }

    // DB-11
    @Test
    void writeNewMessageAppearsInFetchAllMessages(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("gina", "pw", "Gina", "G", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        Message message = db.writeNewMessage("hello", owner.getId(), chat.getId());

        List<String> serialized = db.fetchAllMessages(owner);
        assertTrue(serialized.contains(message.toString()));
    }

    // DB-12
    @Test
    void fetchAllChatsAndMessagesAreEmptyForUserInNoChats(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser lonely = db.writeNewUser("lonely", "pw", "Lone", "Ly", false, false);

        assertTrue(db.fetchAllChats(lonely).isEmpty());
        assertTrue(db.fetchAllMessages(lonely).isEmpty());
    }

    // DB-13
    @Test
    void fetchAllChatsExcludesChatsUserIsNotIn(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser userA = db.writeNewUser("userA", "pw", "A", "A", false, false);
        AbstractUser userB = db.writeNewUser("userB", "pw", "B", "B", false, false);
        AbstractUser userC = db.writeNewUser("userC", "pw", "C", "C", false, false);

        Chat privateChat = db.writeNewChat(userB.getId(), "b-and-c",
                new ArrayList<>(List.of(userC.getId())), true);

        List<String> chatsForA = db.fetchAllChats(userA);
        assertFalse(chatsForA.contains(privateChat.toString()));
    }

    // DB-13b: IT admins moderate, so membership does not gate their visibility.
    @Test
    void adminSeesAllChatsAndMessagesRegardlessOfMembership(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser admin = db.writeNewUser("modAdmin", "pw", "Mod", "Admin", false, true);
        AbstractUser userB = db.writeNewUser("userB2", "pw", "B", "B", false, false);
        AbstractUser userC = db.writeNewUser("userC2", "pw", "C", "C", false, false);

        Chat privateChat = db.writeNewChat(userB.getId(), "b-and-c-2",
                new ArrayList<>(List.of(userC.getId())), true);
        Message message = db.writeNewMessage("secret between B and C", userB.getId(), privateChat.getId());

        List<String> chatsForAdmin = db.fetchAllChats(admin);
        List<String> messagesForAdmin = db.fetchAllMessages(admin);

        assertTrue(chatsForAdmin.contains(privateChat.toString()), "admin must see a chat they're not a member of");
        assertTrue(messagesForAdmin.contains(message.toString()), "admin must see messages from a chat they're not a member of");

        // a non-admin, non-member user must still be excluded, unchanged from DB-13.
        AbstractUser userD = db.writeNewUser("userD", "pw", "D", "D", false, false);
        assertFalse(db.fetchAllChats(userD).contains(privateChat.toString()));
    }

    // DB-14
    @Test
    void ownerCanAddUserToChat(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("owner2", "pw", "Own", "Er", false, false);
        AbstractUser newcomer = db.writeNewUser("newcomer", "pw", "New", "Comer", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        Chat updated = db.addUserToChat(newcomer.getId(), chat.getId(), owner.getId());

        assertTrue(updated.getChattersIds().contains(newcomer.getId()));
    }

    // DB-15
    @Test
    void nonOwnerNonAdminCannotAddUserToChat(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("owner3", "pw", "Own", "Er", false, false);
        AbstractUser rando = db.writeNewUser("rando", "pw", "Ran", "Do", false, false);
        AbstractUser newcomer = db.writeNewUser("newcomer2", "pw", "New", "Comer", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        assertThrows(SecurityException.class,
                () -> db.addUserToChat(newcomer.getId(), chat.getId(), rando.getId()));
    }

    // DB-16
    @Test
    void itAdminCanAddUserToChatTheyDoNotOwn(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("owner4", "pw", "Own", "Er", false, false);
        AbstractUser admin = db.writeNewUser("admin2", "pw", "Ad", "Min", false, true);
        AbstractUser newcomer = db.writeNewUser("newcomer3", "pw", "New", "Comer", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        Chat updated = db.addUserToChat(newcomer.getId(), chat.getId(), admin.getId());
        assertTrue(updated.getChattersIds().contains(newcomer.getId()));
    }

    // DB-17
    @Test
    void ownerCanRemoveUserFromChat(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("owner5", "pw", "Own", "Er", false, false);
        AbstractUser member = db.writeNewUser("member", "pw", "Mem", "Ber", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(List.of(member.getId())), false);

        Chat updated = db.removeUserFromChat(member.getId(), chat.getId(), owner.getId());
        assertFalse(updated.getChattersIds().contains(member.getId()));
    }

    // DB-18
    @Test
    void nonOwnerNonAdminCannotRemoveUserFromChat(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("owner6", "pw", "Own", "Er", false, false);
        AbstractUser member = db.writeNewUser("member2", "pw", "Mem", "Ber", false, false);
        AbstractUser rando = db.writeNewUser("rando2", "pw", "Ran", "Do", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(List.of(member.getId())), false);

        assertThrows(SecurityException.class,
                () -> db.removeUserFromChat(member.getId(), chat.getId(), rando.getId()));
    }

    // DB-19
    @Test
    void renameChatOwnerSucceedsUnrelatedUserFails(@TempDir Path dir) {
        DBManager db = newManager(dir);
        AbstractUser owner = db.writeNewUser("owner7", "pw", "Own", "Er", false, false);
        AbstractUser rando = db.writeNewUser("rando3", "pw", "Ran", "Do", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "old-name", new ArrayList<>(), false);

        Chat renamed = db.renameChat(owner.getId(), chat.getId(), "new-name");
        assertEquals("new-name", renamed.getRoomName());

        assertThrows(SecurityException.class,
                () -> db.renameChat(rando.getId(), chat.getId(), "hijacked"));
    }

    // DB-20
    @Test
    void concurrentUserCreationProducesDistinctIdsAndCorrectFileLineCount(@TempDir Path dir) throws Exception {
        DBManager db = newManager(dir);
        int threadCount = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger index = new AtomicInteger();

        List<java.util.concurrent.Future<String>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    int n = index.incrementAndGet();
                    return db.writeNewUser("concurrent" + n, "pw", "F" + n, "L" + n, false, false).getId();
                }))
                .collect(Collectors.toList());

        ready.await();
        go.countDown();

        Set<String> ids = new java.util.HashSet<>();
        for (var future : futures) {
            ids.add(future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertEquals(threadCount, ids.size(), "all created users must have distinct ids");
        long lineCount = Files.lines(dir.resolve("Users.txt")).count();
        assertEquals(threadCount, lineCount, "no lost updates writing Users.txt under concurrent access");
    }

    // DB-21
    @Test
    void corruptedUserLineFailsLoudlyOnLoad(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Users.txt"), "not/enough/fields\n");
        assertThrows(RuntimeException.class,
                () -> new DBManager(dir.toString(), "Users.txt", "Chats.txt", "Messages.txt"));
    }
}
