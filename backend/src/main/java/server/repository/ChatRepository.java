package server.repository;

import model.AbstractUser;
import model.Chat;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * JdbcTemplate-backed persistence for chats, replacing DBManager's old Chats.txt file.
 * chat_members.user_id carries a foreign key to users(id), so unlike the old flat-file format a
 * chat can never reference a chatter id that doesn't exist.
 */
public class ChatRepository {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;

    public ChatRepository(JdbcTemplate jdbc, UserRepository userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }

    public Chat findById(String id) {
        // See UserRepository.tryParseId: an unrecognized id (including "", the sentinel several
        // client calls use for "no other users") must resolve to "not found", not an exception.
        Integer parsedId = UserRepository.tryParseId(id);
        if (parsedId == null) {
            return null;
        }
        List<Object[]> results = jdbc.query("SELECT id, owner_id, room_name, is_private FROM chats WHERE id = ?",
                (rs, rowNum) -> new Object[]{
                        String.valueOf(rs.getInt("id")),
                        String.valueOf(rs.getInt("owner_id")),
                        rs.getString("room_name"),
                        rs.getBoolean("is_private")
                }, parsedId);
        if (results.isEmpty()) {
            return null;
        }
        return hydrate(results.get(0));
    }

    public List<Chat> findAll() {
        List<String> ids = jdbc.query("SELECT id FROM chats ORDER BY id",
                (rs, rowNum) -> String.valueOf(rs.getInt("id")));
        List<Chat> chats = new ArrayList<>();
        for (String id : ids) {
            chats.add(findById(id));
        }
        return chats;
    }

    private Chat hydrate(Object[] row) {
        String id = (String) row[0];
        String ownerId = (String) row[1];
        String roomName = (String) row[2];
        boolean isPrivate = (boolean) row[3];

        AbstractUser owner = userRepository.findById(ownerId);
        List<String> chatterIds = jdbc.query("SELECT user_id FROM chat_members WHERE chat_id = ?",
                (rs, rowNum) -> String.valueOf(rs.getInt("user_id")), Integer.parseInt(id));
        List<AbstractUser> chatters = new ArrayList<>();
        for (String chatterId : chatterIds) {
            chatters.add(userRepository.findById(chatterId));
        }
        return new Chat(owner, roomName, id, chatters, isPrivate);
    }

    public void insert(Chat chat) {
        jdbc.update("INSERT INTO chats (id, owner_id, room_name, is_private) VALUES (?, ?, ?, ?)",
                Integer.parseInt(chat.getId()), Integer.parseInt(chat.getOwner().getId()),
                chat.getRoomName(), chat.isPrivate());
        for (AbstractUser chatter : chat.getChatters()) {
            addMember(chat.getId(), chatter.getId());
        }
    }

    public void addMember(String chatId, String userId) {
        jdbc.update("INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                Integer.parseInt(chatId), Integer.parseInt(userId));
    }

    public void removeMember(String chatId, String userId) {
        jdbc.update("DELETE FROM chat_members WHERE chat_id = ? AND user_id = ?",
                Integer.parseInt(chatId), Integer.parseInt(userId));
    }

    public void rename(String chatId, String newRoomName) {
        jdbc.update("UPDATE chats SET room_name = ? WHERE id = ?", newRoomName, Integer.parseInt(chatId));
    }

    /** Highest id currently stored, or -1 if the table is empty -- used to bootstrap Chat's id counter. */
    public int maxId() {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(id), -1) FROM chats", Integer.class);
        return max;
    }
}
