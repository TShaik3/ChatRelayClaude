package server;

import model.AbstractUser;
import model.Chat;
import model.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import support.TestDatabase;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DBManagerTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private TestDatabase testDb;

    @BeforeEach
    void setUp() {
        testDb = TestDatabase.createSchema();
    }

    @AfterEach
    void tearDown() {
        testDb.close();
    }

    private DBManager newManager() {
        return new DBManager(testDb.dataSource());
    }

    // DB-1
    @Test
    void constructingAgainstFreshSchemaStartsEmpty() {
        DBManager db = newManager();

        assertTrue(db.listAllUsers().isEmpty());
    }

    // DB-2
    @Test
    void writeNewUserIsFindableByIdAndUsername() {
        DBManager db = newManager();
        AbstractUser user = db.writeNewUser("alice", "pw", "Alice", "A", false, false);

        assertEquals(user, db.getUserById(user.getId()));
        assertEquals(user, db.getUserByUsername("alice"));
    }

    // DB-3
    @Test
    void writeNewUserRejectsDuplicateUsername() {
        DBManager db = newManager();
        db.writeNewUser("alice", "pw", "Alice", "A", false, false);

        assertThrows(IllegalArgumentException.class,
                () -> db.writeNewUser("alice", "different-pw", "Alice2", "A2", false, false));

        assertEquals(1, testDb.countRows("users"), "rejected duplicate must not add a row");
    }

    // DB-4 / DB-5
    @Test
    void reloadingFromDatabaseRestoresUsersAndAvoidsIdCollisions() {
        DBManager first = newManager();
        AbstractUser original = first.writeNewUser("bob", "pw", "Bob", "B", false, false);

        // A second DBManager against the same schema simulates a process restart: Postgres, not
        // an in-memory map, is the source of truth, so this must see what `first` wrote.
        DBManager reloaded = newManager();
        AbstractUser fromDb = reloaded.getUserById(original.getId());
        assertEquals(original.getUserName(), fromDb.getUserName());
        assertEquals(original.getFirstName(), fromDb.getFirstName());

        AbstractUser afterRestart = reloaded.writeNewUser("carol", "pw", "Carol", "C", false, false);
        assertNotEquals(original.getId(), afterRestart.getId());
        assertTrue(Integer.parseInt(afterRestart.getId()) > Integer.parseInt(original.getId()));
    }

    // Passwords must never be stored in plaintext -- this pins the BCrypt hashing behavior added
    // when the flat-file store (which persisted raw passwords) was replaced by Postgres. Login
    // itself now goes through Spring Security's DaoAuthenticationProvider (see AuthControllerTest),
    // not a DBManager method, so this only needs to prove the hash, not a login round-trip.
    @Test
    void writeNewUserStoresAHashNotThePlaintextPassword() {
        DBManager db = newManager();
        AbstractUser user = db.writeNewUser("hashcheck", "super-secret", "Hash", "Check", false, false);

        assertNotEquals("super-secret", user.getPassword());
        assertTrue(PASSWORD_ENCODER.matches("super-secret", user.getPassword()));
    }

    @Test
    void updateUserDetailsChangesEverythingAndPersists() {
        DBManager db = newManager();
        AbstractUser user = db.writeNewUser("original", "origpw", "Orig", "Inal", false, false);

        AbstractUser updated = db.updateUserDetails(user.getId(), "renamed", "Ren", "Amed", true, true, "newpw");

        assertEquals("renamed", updated.getUserName());
        assertEquals("Ren", updated.getFirstName());
        assertEquals("Amed", updated.getLastName());
        assertTrue(updated.isDisabled());
        assertTrue(updated.isAdmin());
        assertTrue(PASSWORD_ENCODER.matches("newpw", updated.getPassword()));
        assertEquals(user.getId(), updated.getId(), "id must never change");

        DBManager reloaded = newManager();
        AbstractUser fromDb = reloaded.getUserById(user.getId());
        assertEquals("renamed", fromDb.getUserName());
        assertTrue(PASSWORD_ENCODER.matches("newpw", fromDb.getPassword()));
        assertTrue(fromDb.isAdmin());
        assertTrue(fromDb.isDisabled(), "isDisabled must persist across reload same as every other field");
    }

    @Test
    void updateUserDetailsWithBlankPasswordLeavesPasswordUnchanged() {
        DBManager db = newManager();
        AbstractUser user = db.writeNewUser("keeppw", "keep-this-password", "K", "P", false, false);

        AbstractUser updated = db.updateUserDetails(user.getId(), "keeppw", "K2", "P2", false, false, "");

        assertTrue(PASSWORD_ENCODER.matches("keep-this-password", updated.getPassword()));
    }

    @Test
    void updateUserDetailsRejectsUsernameAlreadyTakenByAnotherUser() {
        DBManager db = newManager();
        AbstractUser userA = db.writeNewUser("userTaken", "pw", "A", "A", false, false);
        AbstractUser userB = db.writeNewUser("userFree", "pw", "B", "B", false, false);

        assertThrows(IllegalArgumentException.class,
                () -> db.updateUserDetails(userB.getId(), "userTaken", "B", "B", false, false, ""));

        // renaming a user to their OWN current username must not spuriously conflict with themself
        AbstractUser unchanged = db.updateUserDetails(userA.getId(), "userTaken", "A2", "A", false, false, "");
        assertEquals("A2", unchanged.getFirstName());
    }

    @Test
    void updateUserDetailsRejectsUnknownUser() {
        DBManager db = newManager();
        assertThrows(IllegalArgumentException.class,
                () -> db.updateUserDetails("no-such-id", "x", "X", "X", false, false, ""));
    }

    // DB-9
    @Test
    void writeNewChatAlwaysIncludesOwnerEvenIfNotInChatterList() {
        DBManager db = newManager();
        AbstractUser owner = db.writeNewUser("owner", "pw", "Own", "Er", false, false);
        AbstractUser other = db.writeNewUser("other", "pw", "Oth", "Er", false, false);

        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(List.of(other.getId())), false);

        assertTrue(chat.getChattersIds().contains(owner.getId()));
        assertTrue(chat.getChattersIds().contains(other.getId()));
    }

    // DB-10
    @Test
    void writeNewChatRejectsUnknownOwner() {
        DBManager db = newManager();
        assertThrows(IllegalArgumentException.class,
                () -> db.writeNewChat("no-such-user", "room", new ArrayList<>(), false));
    }

    // DB-11
    @Test
    void writeNewMessageAppearsInFetchMessagesForChat() {
        DBManager db = newManager();
        AbstractUser owner = db.writeNewUser("gina", "pw", "Gina", "G", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        Message message = db.writeNewMessage("hello", owner.getId(), chat.getId());

        assertTrue(db.fetchMessagesForChat(owner, chat.getId()).contains(message));
    }

    // DB-12
    @Test
    void listChatsVisibleToIsEmptyForUserInNoChats() {
        DBManager db = newManager();
        AbstractUser lonely = db.writeNewUser("lonely", "pw", "Lone", "Ly", false, false);

        assertTrue(db.listChatsVisibleTo(lonely).isEmpty());
    }

    @Test
    void fetchMessagesForChatRejectsNonMemberNonAdmin() {
        DBManager db = newManager();
        AbstractUser lonely = db.writeNewUser("lonely2", "pw", "Lone", "Ly", false, false);
        AbstractUser owner = db.writeNewUser("chatowner", "pw", "Own", "Er", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        assertThrows(SecurityException.class, () -> db.fetchMessagesForChat(lonely, chat.getId()));
    }

    // DB-13
    @Test
    void listChatsVisibleToExcludesChatsUserIsNotIn() {
        DBManager db = newManager();
        AbstractUser userA = db.writeNewUser("userA", "pw", "A", "A", false, false);
        AbstractUser userB = db.writeNewUser("userB", "pw", "B", "B", false, false);
        AbstractUser userC = db.writeNewUser("userC", "pw", "C", "C", false, false);

        Chat privateChat = db.writeNewChat(userB.getId(), "b-and-c",
                new ArrayList<>(List.of(userC.getId())), true);

        assertFalse(db.listChatsVisibleTo(userA).contains(privateChat));
    }

    // DB-13b: IT admins moderate, so membership does not gate their visibility.
    @Test
    void adminSeesAllChatsAndMessagesRegardlessOfMembership() {
        DBManager db = newManager();
        AbstractUser admin = db.writeNewUser("modAdmin", "pw", "Mod", "Admin", false, true);
        AbstractUser userB = db.writeNewUser("userB2", "pw", "B", "B", false, false);
        AbstractUser userC = db.writeNewUser("userC2", "pw", "C", "C", false, false);

        Chat privateChat = db.writeNewChat(userB.getId(), "b-and-c-2",
                new ArrayList<>(List.of(userC.getId())), true);
        Message message = db.writeNewMessage("secret between B and C", userB.getId(), privateChat.getId());

        assertTrue(db.listChatsVisibleTo(admin).contains(privateChat), "admin must see a chat they're not a member of");
        assertTrue(db.fetchMessagesForChat(admin, privateChat.getId()).contains(message),
                "admin must see messages from a chat they're not a member of");

        // a non-admin, non-member user must still be excluded, unchanged from DB-13.
        AbstractUser userD = db.writeNewUser("userD", "pw", "D", "D", false, false);
        assertFalse(db.listChatsVisibleTo(userD).contains(privateChat));
    }

    // DB-14
    @Test
    void ownerCanAddUserToChat() {
        DBManager db = newManager();
        AbstractUser owner = db.writeNewUser("owner2", "pw", "Own", "Er", false, false);
        AbstractUser newcomer = db.writeNewUser("newcomer", "pw", "New", "Comer", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        Chat updated = db.addUserToChat(newcomer.getId(), chat.getId(), owner.getId());

        assertTrue(updated.getChattersIds().contains(newcomer.getId()));
    }

    // DB-15
    @Test
    void nonOwnerNonAdminCannotAddUserToChat() {
        DBManager db = newManager();
        AbstractUser owner = db.writeNewUser("owner3", "pw", "Own", "Er", false, false);
        AbstractUser rando = db.writeNewUser("rando", "pw", "Ran", "Do", false, false);
        AbstractUser newcomer = db.writeNewUser("newcomer2", "pw", "New", "Comer", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        assertThrows(SecurityException.class,
                () -> db.addUserToChat(newcomer.getId(), chat.getId(), rando.getId()));
    }

    // DB-16
    @Test
    void itAdminCanAddUserToChatTheyDoNotOwn() {
        DBManager db = newManager();
        AbstractUser owner = db.writeNewUser("owner4", "pw", "Own", "Er", false, false);
        AbstractUser admin = db.writeNewUser("admin2", "pw", "Ad", "Min", false, true);
        AbstractUser newcomer = db.writeNewUser("newcomer3", "pw", "New", "Comer", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(), false);

        Chat updated = db.addUserToChat(newcomer.getId(), chat.getId(), admin.getId());
        assertTrue(updated.getChattersIds().contains(newcomer.getId()));
    }

    // DB-17
    @Test
    void ownerCanRemoveUserFromChat() {
        DBManager db = newManager();
        AbstractUser owner = db.writeNewUser("owner5", "pw", "Own", "Er", false, false);
        AbstractUser member = db.writeNewUser("member", "pw", "Mem", "Ber", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(List.of(member.getId())), false);

        Chat updated = db.removeUserFromChat(member.getId(), chat.getId(), owner.getId());
        assertFalse(updated.getChattersIds().contains(member.getId()));
    }

    // DB-18
    @Test
    void nonOwnerNonAdminCannotRemoveUserFromChat() {
        DBManager db = newManager();
        AbstractUser owner = db.writeNewUser("owner6", "pw", "Own", "Er", false, false);
        AbstractUser member = db.writeNewUser("member2", "pw", "Mem", "Ber", false, false);
        AbstractUser rando = db.writeNewUser("rando2", "pw", "Ran", "Do", false, false);
        Chat chat = db.writeNewChat(owner.getId(), "room", new ArrayList<>(List.of(member.getId())), false);

        assertThrows(SecurityException.class,
                () -> db.removeUserFromChat(member.getId(), chat.getId(), rando.getId()));
    }

    // DB-19
    @Test
    void renameChatOwnerSucceedsUnrelatedUserFails() {
        DBManager db = newManager();
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
    void concurrentUserCreationProducesDistinctIdsAndNoLostWrites() throws Exception {
        DBManager db = newManager();
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
        assertEquals(threadCount, testDb.countRows("users"), "no lost updates writing users under concurrent access");
    }
}
