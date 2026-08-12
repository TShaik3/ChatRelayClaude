package server.support;

import model.AbstractUser;
import model.Chat;
import model.ITAdmin;
import model.Message;
import model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import packet.Packet;
import server.repository.ChatRepository;
import server.repository.MessageRepository;
import server.repository.UserRepository;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-off import of the legacy Users.txt/Chats.txt/Messages.txt flat files (Migration Plan
 * Phase 1) into Postgres. Run once against a fresh, empty schema: `./gradlew run` wiring aside,
 * invoke directly as `java -cp ... server.support.MigrateFlatFilesToPostgres [flatFileDir]`.
 *
 * Ids are preserved exactly as they appear in the files -- NOT reassigned through DBManager's
 * normal id counter -- since Chats.txt and Messages.txt reference specific user/chat ids that
 * must still resolve correctly after import. Plaintext passwords are hashed with BCrypt, since
 * the old files stored them in cleartext and Postgres never should.
 */
public final class MigrateFlatFilesToPostgres {

    private MigrateFlatFilesToPostgres() {
    }

    public static void main(String[] args) throws IOException {
        Path flatFileDir = Path.of(args.length > 0 ? args[0] : "./dbFiles/development");
        migrate(flatFileDir, DataSources.devDataSource());
    }

    static void migrate(Path dir, DataSource dataSource) throws IOException {
        Migrations.migrate(dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UserRepository userRepository = new UserRepository(jdbc);
        ChatRepository chatRepository = new ChatRepository(jdbc, userRepository);
        MessageRepository messageRepository = new MessageRepository(jdbc);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        Map<String, AbstractUser> usersById = migrateUsers(dir.resolve("Users.txt"), userRepository, passwordEncoder);
        Map<String, Chat> chatsById = migrateChats(dir.resolve("Chats.txt"), chatRepository, usersById);
        migrateMessages(dir.resolve("Messages.txt"), messageRepository, usersById, chatsById);
    }

    private static Map<String, AbstractUser> migrateUsers(Path file, UserRepository userRepository,
                                                            PasswordEncoder passwordEncoder) throws IOException {
        Map<String, AbstractUser> usersById = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("/");
            String username = Packet.unsanitize(parts[0]);
            String plaintextPassword = Packet.unsanitize(parts[1]);
            String id = parts[2];
            String firstName = Packet.unsanitize(parts[3]);
            String lastName = Packet.unsanitize(parts[4]);
            boolean isDisabled = Boolean.parseBoolean(parts[5]);
            boolean isAdmin = Boolean.parseBoolean(parts[6]);

            String hashedPassword = passwordEncoder.encode(plaintextPassword);
            AbstractUser user = isAdmin
                    ? new ITAdmin(username, hashedPassword, id, firstName, lastName, isDisabled, true)
                    : new User(username, hashedPassword, id, firstName, lastName, isDisabled, false);
            userRepository.insert(user);
            usersById.put(id, user);
        }
        return usersById;
    }

    private static Map<String, Chat> migrateChats(Path file, ChatRepository chatRepository,
                                                    Map<String, AbstractUser> usersById) throws IOException {
        Map<String, Chat> chatsById = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("/");
            String id = parts[0];
            String ownerId = parts[1];
            String roomName = Packet.unsanitize(parts[2]);
            boolean isPrivate = Boolean.parseBoolean(parts[3]);

            List<AbstractUser> chatters = new ArrayList<>();
            if (parts.length > 4 && !parts[4].isEmpty()) {
                for (String chatterId : parts[4].split(",")) {
                    AbstractUser chatter = usersById.get(chatterId);
                    if (chatter != null) {
                        chatters.add(chatter);
                    }
                }
            }
            AbstractUser owner = usersById.get(ownerId);
            Chat chat = new Chat(owner, roomName, id, chatters, isPrivate);
            chatRepository.insert(chat);
            chatsById.put(id, chat);
        }
        return chatsById;
    }

    private static void migrateMessages(Path file, MessageRepository messageRepository,
                                         Map<String, AbstractUser> usersById, Map<String, Chat> chatsById) throws IOException {
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("/");
            String id = parts[0];
            long createdAt = Long.parseLong(parts[1]);
            String content = Packet.unsanitize(parts[2]);
            String authorId = parts[3];
            String chatId = parts[4];

            AbstractUser author = usersById.get(authorId);
            Chat chat = chatsById.get(chatId);
            if (author == null || chat == null) continue;
            messageRepository.insert(new Message(id, createdAt, content, author, chat));
        }
    }
}
