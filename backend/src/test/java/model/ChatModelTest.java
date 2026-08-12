package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModelTest {

    private User user(String username) {
        return new User(username + "_" + System.nanoTime(), "pw", "First", "Last", false, false);
    }

    // CHT-1
    // NOTE: Chat's own constructor only links whichever users are passed in `chatters` —
    // it does not implicitly add the owner. Callers (DBManager.writeNewChat, Client) always
    // pass the owner explicitly, so tests do the same here to match real usage.
    @Test
    void constructorLinksOwnerAndChattersBothWays() {
        User owner = user("owner");
        User other = user("other");
        Chat chat = new Chat(owner, "room", new ArrayList<>(List.of(owner, other)), false);

        assertTrue(chat.getChatters().contains(owner));
        assertTrue(chat.getChatters().contains(other));
        assertTrue(owner.getChats().contains(chat));
        assertTrue(other.getChats().contains(chat));
    }

    // CHT-2
    @Test
    void addChatterTwiceDoesNotDuplicate() {
        User owner = user("owner2");
        User other = user("other2");
        Chat chat = new Chat(owner, "room", new ArrayList<>(), false);

        chat.addChatter(other);
        chat.addChatter(other);

        assertEquals(1, chat.getChatters().stream().filter(u -> u == other).count());
    }

    // CHT-3
    @Test
    void removeChatterUpdatesBothSides() {
        User owner = user("owner3");
        User other = user("other3");
        Chat chat = new Chat(owner, "room", new ArrayList<>(List.of(other)), false);

        chat.removeChatter(other);

        assertFalse(chat.getChatters().contains(other));
        assertFalse(other.getChats().contains(chat));
    }

    // CHT-4
    @Test
    void addMessagePreservesInsertionOrder() {
        User owner = user("owner4");
        Chat chat = new Chat(owner, "room", new ArrayList<>(), false);

        Message m1 = new Message("first", owner, chat);
        Message m2 = new Message("second", owner, chat);
        chat.addMessage(m1);
        chat.addMessage(m2);

        assertEquals(List.of(m1, m2), chat.getMessages());
    }

    // CHT-5
    @Test
    void changePrivacyUpdatesFlag() {
        User owner = user("owner5");
        Chat chat = new Chat(owner, "room", new ArrayList<>(), false);
        assertFalse(chat.isPrivate());
        chat.changePrivacy(true);
        assertTrue(chat.isPrivate());
    }

    // CHT-6
    @Test
    void roomNameWithSlashIsStoredExactly() {
        User owner = user("owner6");
        Chat chat = new Chat(owner, "room/with/slashes", new ArrayList<>(), false);

        // No wire-format sanitization to worry about anymore -- the room name is a plain field,
        // not a "/"-delimited string that needed slashes escaped (see the retired Packet class).
        assertEquals("room/with/slashes", chat.getRoomName());
    }

    // CHT-7
    @Test
    void getChattersIdsIncludesEveryChatter() {
        User owner = user("owner7");
        User other = user("other7");
        Chat chat = new Chat(owner, "general", new ArrayList<>(List.of(owner, other)), false);

        assertTrue(chat.getChattersIds().contains(owner.getId()));
        assertTrue(chat.getChattersIds().contains(other.getId()));
    }

    // CHT-8
    @Test
    void getChattersIdsWithOwnerOnlyHasExactlyOneEntry() {
        User owner = user("owner8");
        Chat chat = new Chat(owner, "solo", new ArrayList<>(List.of(owner)), false);

        assertEquals(List.of(owner.getId()), chat.getChattersIds());
    }
}
