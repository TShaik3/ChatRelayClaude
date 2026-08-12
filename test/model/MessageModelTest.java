package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageModelTest {

    private User user(String username) {
        return new User(username + "_" + System.nanoTime(), "pw", "First", "Last", false, false);
    }

    // MSG-1
    @Test
    void newMessageConstructorStampsPlausibleCurrentTime() {
        User author = user("author1");
        Chat chat = new Chat(author, "room", new ArrayList<>(List.of(author)), false);

        long before = System.currentTimeMillis() / 1000L - 2;
        Message message = new Message("hi", author, chat);
        long after = System.currentTimeMillis() / 1000L + 2;

        assertTrue(message.getCreatedAt() >= before && message.getCreatedAt() <= after);
    }

    // MSG-2
    @Test
    void loadFromStorageConstructorPreservesGivenIdAndTimestamp() {
        User author = user("author2");
        Chat chat = new Chat(author, "room", new ArrayList<>(List.of(author)), false);

        Message message = new Message("777", 123456789L, "hi", author, chat);

        assertEquals("777", message.getId());
        assertEquals(123456789L, message.getCreatedAt());
    }

    // MSG-3
    @Test
    void contentWithSlashRoundTripsThroughToString() {
        User author = user("author3");
        Chat chat = new Chat(author, "room", new ArrayList<>(List.of(author)), false);
        Message message = new Message("part1/part2/part3", author, chat);

        String[] parts = message.toString().split("/");
        assertEquals("part1/part2/part3", packet.Packet.unsanitize(parts[2]));
    }

    // MSG-4
    @Test
    void toStringFormatMatchesSpec() {
        User author = user("author4");
        Chat chat = new Chat(author, "room", new ArrayList<>(List.of(author)), false);
        Message message = new Message("hello", author, chat);

        String expected = message.getId() + "/" + message.getCreatedAt() + "/hello/"
                + author.getId() + "/" + chat.getId();
        assertEquals(expected, message.toString());
    }
}
