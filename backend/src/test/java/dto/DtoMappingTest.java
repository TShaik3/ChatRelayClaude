package dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.AbstractUser;
import model.Chat;
import model.ITAdmin;
import model.Message;
import model.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Phase 3 REST/WebSocket layer's DTOs correctly represent the domain models and
 * actually round-trip through Jackson -- ahead of any controller existing to exercise them.
 */
class DtoMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void userDtoCarriesClientSafeFieldsAndNeverThePasswordHash() throws Exception {
        AbstractUser user = new User("alice", "$2a$10$hashedvalue", "Alice", "A", false, false);

        UserDto dto = UserDto.from(user);

        assertEquals(user.getId(), dto.id());
        assertEquals("alice", dto.username());
        assertEquals("Alice", dto.firstName());
        assertEquals("A", dto.lastName());
        assertFalse(dto.disabled());
        assertFalse(dto.admin());

        // Structural guarantee, not just a string-content check: UserDto has no field capable of
        // holding a password at all, unlike the old toStringClient() which had to be trusted not
        // to include one.
        String json = mapper.writeValueAsString(dto);
        assertFalse(json.contains("hashedvalue"));

        UserDto roundTripped = mapper.readValue(json, UserDto.class);
        assertEquals(dto, roundTripped);
    }

    @Test
    void userDtoReflectsAdminAndDisabledFlags() {
        AbstractUser admin = new ITAdmin("adminuser", "pw", "Ad", "Min", true, true);

        UserDto dto = UserDto.from(admin);

        assertTrue(dto.admin());
        assertTrue(dto.disabled());
    }

    @Test
    void chatDtoIncludesOwnerAndAllChatterIds() throws Exception {
        AbstractUser owner = new User("owner", "pw", "Own", "Er", false, false);
        AbstractUser member = new User("member", "pw", "Mem", "Ber", false, false);
        // The Chat constructor only adds whoever is passed in `chatters` -- unlike
        // DBManager.writeNewChat, it does not implicitly add the owner, so it must be listed here.
        Chat chat = new Chat(owner, "room", List.of(owner, member), false);

        ChatDto dto = ChatDto.from(chat);

        assertEquals(chat.getId(), dto.id());
        assertEquals(owner.getId(), dto.ownerId());
        assertEquals("room", dto.roomName());
        assertFalse(dto.isPrivate());
        assertTrue(dto.chatterIds().contains(owner.getId()));
        assertTrue(dto.chatterIds().contains(member.getId()));

        String json = mapper.writeValueAsString(dto);
        ChatDto roundTripped = mapper.readValue(json, ChatDto.class);
        assertEquals(dto, roundTripped);
    }

    @Test
    void messageDtoReflectsAuthorAndChat() throws Exception {
        AbstractUser author = new User("author", "pw", "Au", "Thor", false, false);
        Chat chat = new Chat(author, "room", List.of(), false);
        Message message = new Message("hello", author, chat);

        MessageDto dto = MessageDto.from(message);

        assertEquals(message.getId(), dto.id());
        assertEquals(message.getCreatedAt(), dto.createdAt());
        assertEquals("hello", dto.content());
        assertEquals(author.getId(), dto.authorId());
        assertEquals(chat.getId(), dto.chatId());

        String json = mapper.writeValueAsString(dto);
        MessageDto roundTripped = mapper.readValue(json, MessageDto.class);
        assertEquals(dto, roundTripped);
    }
}
