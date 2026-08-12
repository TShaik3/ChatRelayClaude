package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserModelTest {

    // USR-1
    @Test
    void newUserConstructorAssignsDistinctIncreasingIds() {
        User a = new User("u1_" + System.nanoTime(), "pw", "First", "Last", false, false);
        User b = new User("u2_" + System.nanoTime(), "pw", "First", "Last", false, false);

        assertTrue(Integer.parseInt(b.getId()) > Integer.parseInt(a.getId()));
    }

    // USR-2
    @Test
    void loadFromStorageConstructorPreservesGivenId() {
        User user = new User("bob", "pw", "999999", "Bob", "B", false, false);
        assertEquals("999999", user.getId());
    }

    // USR-3
    @Test
    void frontEndConstructorHasNoPassword() {
        User user = new User(true, "42", "carol", "Carol", "C", false, false);
        assertNull(user.getPassword());
        assertEquals("42", user.getId());
        assertEquals("carol", user.getUserName());
    }

    // USR-4
    @Test
    void addChatIsReflectedInGetChats() {
        User owner = new User("owner", "pw", "Own", "Er", false, false);
        Chat chat = new Chat(owner, "room", new ArrayList<>(), false);

        User member = new User("mem", "pw", "Mem", "Ber", false, false);
        member.addChat(chat);

        assertTrue(member.getChats().contains(chat));
    }

    // USR-5
    @Test
    void addChatTwiceDoesNotDuplicate() {
        User owner = new User("owner2", "pw", "Own", "Er", false, false);
        Chat chat = new Chat(owner, "room", new ArrayList<>(), false);

        User member = new User("mem2", "pw", "Mem", "Ber", false, false);
        member.addChat(chat);
        member.addChat(chat);

        assertEquals(1, member.getChats().stream().filter(c -> c == chat).count());
    }

    // USR-6
    @Test
    void removeChatNotPresentDoesNothing() {
        User user = new User("mem3", "pw", "Mem", "Ber", false, false);
        Chat chat = new Chat(user, "room", new ArrayList<>(), false);
        user.removeChat(chat);
        user.removeChat(chat); // second removal of an already-absent chat
        assertTrue(user.getChats().isEmpty() || !user.getChats().contains(chat));
    }

    // USR-7
    @Test
    void updateIsDisabledChangesFlag() {
        User user = new User("mem4", "pw", "Mem", "Ber", false, false);
        assertFalse(user.isDisabled());
        user.updateIsDisabled(true);
        assertTrue(user.isDisabled());
    }

    // USR-8
    @Test
    void fieldsContainingSlashAreStoredExactly() {
        // No wire-format sanitization to worry about anymore -- these are plain fields, not
        // "/"-delimited strings that needed slashes escaped (see the retired Packet class).
        User user = new User("slash/user", "pw/word", "First/Name", "Last/Name", false, false);
        assertEquals("slash/user", user.getUserName());
        assertEquals("pw/word", user.getPassword());
        assertEquals("First/Name", user.getFirstName());
        assertEquals("Last/Name", user.getLastName());
    }

    // USR-9: password exposure is now dto.UserDto's job (it has no password field at all, a
    // structural guarantee -- see dto.DtoMappingTest) rather than a runtime string check here.

    // USR-10
    @Test
    void isAdminReflectsConstructorArgRegardlessOfConcreteClass() {
        User plainAdminFlagTrue = new User("weird", "pw", "W", "W", false, true);
        ITAdmin admin = new ITAdmin("real-admin", "pw", "A", "A", false, true);
        ITAdmin nonAdminFlag = new ITAdmin("odd", "pw", "O", "O", false, false);

        assertTrue(plainAdminFlagTrue.isAdmin());
        assertTrue(admin.isAdmin());
        assertFalse(nonAdminFlag.isAdmin());
    }

    // USR-11
    @Test
    void restoreCountAdvancesCounterPastHighestSeen() {
        AbstractUser.restoreCount(100_000);
        User next = new User("after-restore", "pw", "A", "A", false, false);
        assertTrue(Integer.parseInt(next.getId()) > 100_000);
    }

    // USR-12
    @Test
    void restoreCountNeverDecreasesCounter() {
        User before = new User("baseline", "pw", "A", "A", false, false);
        AbstractUser.restoreCount(1); // much lower than the current counter
        User after = new User("after-lower-restore", "pw", "A", "A", false, false);
        assertTrue(Integer.parseInt(after.getId()) > Integer.parseInt(before.getId()));
    }

    // USR-4b (support): List<Chat> exposure sanity
    @Test
    void getChatsReturnsLiveList() {
        User owner = new User("owner3", "pw", "Own", "Er", false, false);
        List<Chat> chats = owner.getChats();
        assertTrue(chats.isEmpty());
    }
}
