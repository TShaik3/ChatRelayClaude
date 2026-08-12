package server.repository;

import model.AbstractUser;
import model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import packet.Packet;

import java.util.List;

/** JdbcTemplate-backed persistence for messages, replacing DBManager's old Messages.txt file. */
public class MessageRepository {

    private final JdbcTemplate jdbc;

    public MessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    /** Highest id currently stored, or -1 if the table is empty -- used to bootstrap Message's id counter. */
    public int maxId() {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(id), -1) FROM messages", Integer.class);
        return max;
    }
}
