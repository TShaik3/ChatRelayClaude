package server.repository;

import model.AbstractUser;
import model.Chat;
import model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import packet.Packet;

import java.util.ArrayList;
import java.util.List;

/** JdbcTemplate-backed persistence for messages, replacing DBManager's old Messages.txt file. */
public class MessageRepository {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;

    public MessageRepository(JdbcTemplate jdbc, UserRepository userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }

    public void insert(Message message) {
        jdbc.update("INSERT INTO messages (id, created_at, content, author_id, chat_id) VALUES (?, ?, ?, ?, ?)",
                Integer.parseInt(message.getId()), message.getCreatedAt(), message.getContent(),
                Integer.parseInt(message.getSender().getId()), Integer.parseInt(message.getChat().getId()));
    }

    /**
     * Wire-format strings (id/createdAt/content/authorId/chatId) for every message the given
     * user may see -- every message for an IT admin (moderation), otherwise only messages in
     * chats the user belongs to. Formatted directly from the row rather than rehydrating full
     * Message/Chat/AbstractUser objects, since that's all DBManager.fetchAllMessages needs.
     */
    public List<String> findVisibleToAsStrings(AbstractUser user) {
        var rowMapper = (org.springframework.jdbc.core.RowMapper<String>) (rs, rowNum) ->
                rs.getInt("id") + "/" + rs.getLong("created_at") + "/" + Packet.sanitize(rs.getString("content"))
                        + "/" + rs.getInt("author_id") + "/" + rs.getInt("chat_id");

        if (user.isAdmin()) {
            return jdbc.query("SELECT id, created_at, content, author_id, chat_id FROM messages ORDER BY id",
                    rowMapper);
        }
        return jdbc.query("SELECT m.id, m.created_at, m.content, m.author_id, m.chat_id FROM messages m " +
                        "JOIN chat_members cm ON cm.chat_id = m.chat_id WHERE cm.user_id = ? ORDER BY m.id",
                rowMapper, Integer.parseInt(user.getId()));
    }

    /**
     * Full Message domain objects for one chat, for the REST layer's GET /api/chats/{id}/messages
     * to map into DTOs itself. Takes the already-hydrated Chat rather than re-fetching it, since
     * the caller (DBManager.fetchMessagesForChat) already loaded it to check visibility.
     */
    public List<Message> findByChatId(Chat chat) {
        List<Object[]> rows = jdbc.query(
                "SELECT id, created_at, content, author_id FROM messages WHERE chat_id = ? ORDER BY id",
                (rs, rowNum) -> new Object[]{
                        String.valueOf(rs.getInt("id")), rs.getLong("created_at"),
                        rs.getString("content"), String.valueOf(rs.getInt("author_id"))
                }, Integer.parseInt(chat.getId()));

        List<Message> messages = new ArrayList<>();
        for (Object[] row : rows) {
            AbstractUser author = userRepository.findById((String) row[3]);
            messages.add(new Message((String) row[0], (long) row[1], (String) row[2], author, chat));
        }
        return messages;
    }

    /** Highest id currently stored, or -1 if the table is empty -- used to bootstrap Message's id counter. */
    public int maxId() {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(id), -1) FROM messages", Integer.class);
        return max;
    }
}
