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
    void roomNameWithSlashRoundTripsThroughToString() {
        User owner = user("owner6");
        Chat chat = new Chat(owner, "room/with/slashes", new ArrayList<>(), false);

        String[] parts = chat.toString().split("/", -1);
        // id/ownerId/roomName/isPrivate/chatterIds ; roomName itself is sanitized
        assertEquals("room/with/slashes", packet.Packet.unsanitize(parts[2]));
    }

    // CHT-7
    @Test
    void toStringFormatMatchesSpec() {
        User owner = user("owner7");
        User other = user("other7");
        Chat chat = new Chat(owner, "general", new ArrayList<>(List.of(owner, other)), false);

        String expectedPrefix = chat.getId() + "/" + owner.getId() + "/general/false/";
        assertTrue(chat.toString().startsWith(expectedPrefix));
        assertTrue(chat.toString().contains(owner.getId()));
        assertTrue(chat.toString().contains(other.getId()));
    }

    // CHT-8
    @Test
    void toStringWithOwnerOnlyHasNoTrailingComma() {
        User owner = user("owner8");
        Chat chat = new Chat(owner, "solo", new ArrayList<>(List.of(owner)), false);

        String[] parts = chat.toString().split("/");
        String chatterField = parts[4];
        assertEquals(owner.getId(), chatterField);
        assertFalse(chatterField.contains(","));
    }
}
