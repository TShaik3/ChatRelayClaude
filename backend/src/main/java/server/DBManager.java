package server;

import model.AbstractUser;
import model.Chat;
import model.ITAdmin;
import model.Message;
import model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import server.repository.ChatRepository;
import server.repository.MessageRepository;
import server.repository.UserRepository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for users, chats and messages, backed by PostgreSQL via the
 * repository classes in server.repository. Postgres is the sole source of
 * truth -- there is no in-memory cache -- so id counters (AbstractUser/Chat/
 * Message) are bootstrapped from the current max id in each table on
 * construction, mirroring what the old flat-file loader used to do by
 * scanning every line.
 */
public class DBManager {

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DBManager(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        this.userRepository = new UserRepository(jdbc);
        this.chatRepository = new ChatRepository(jdbc, userRepository);
        this.messageRepository = new MessageRepository(jdbc, userRepository);

        restoreIdCounters();
    }

    private void restoreIdCounters() {
        int highestUserId = userRepository.maxId();
        if (highestUserId >= 0) {
            AbstractUser.restoreCount(highestUserId);
        }
        int highestChatId = chatRepository.maxId();
        if (highestChatId >= 0) {
            Chat.restoreCount(highestChatId);
        }
        int highestMessageId = messageRepository.maxId();
        if (highestMessageId >= 0) {
            Message.restoreCount(highestMessageId);
        }
    }

    public ArrayList<String> fetchAllUsers() {
        ArrayList<String> result = new ArrayList<>();
        for (AbstractUser user : userRepository.findAll()) {
            result.add(user.toStringClient());
        }
        return result;
    }

    /** IT admins see every chat, membership aside, for moderation purposes. */
    public ArrayList<String> fetchAllChats(AbstractUser user) {
        ArrayList<String> result = new ArrayList<>();
        for (Chat chat : chatRepository.findAll()) {
            if (user.isAdmin() || chat.getChatters().contains(user)) {
                result.add(chat.toString());
            }
        }
        return result;
    }

    /** IT admins see every message, membership aside, for moderation purposes. */
    public ArrayList<String> fetchAllMessages(AbstractUser user) {
        return new ArrayList<>(messageRepository.findVisibleToAsStrings(user));
    }

    /** Domain-object equivalent of fetchAllUsers(), for the REST layer to map into DTOs itself. */
    public List<AbstractUser> listAllUsers() {
        return userRepository.findAll();
    }

    /** Domain-object equivalent of fetchAllChats(user), for the REST layer to map into DTOs itself. */
    public List<Chat> listChatsVisibleTo(AbstractUser user) {
        List<Chat> result = new ArrayList<>();
        for (Chat chat : chatRepository.findAll()) {
            if (user.isAdmin() || chat.getChatters().contains(user)) {
                result.add(chat);
            }
        }
        return result;
    }

    /**
     * Messages for a single chat (not every chat the user can see, unlike fetchAllMessages) --
     * the REST layer's GET /api/chats/{id}/messages loads one chat's history at a time rather
     * than dumping everything up front the way the socket protocol's login flow used to.
     */
    public List<Message> fetchMessagesForChat(AbstractUser user, String chatId) {
        Chat chat = getChatById(chatId);
        if (chat == null) {
            throw new IllegalArgumentException("No such chat: " + chatId);
        }
        if (!user.isAdmin() && !chat.getChatters().contains(user)) {
            throw new SecurityException("User " + user.getId() + " may not view chat " + chatId);
        }
        return messageRepository.findByChatId(chat);
    }

    public AbstractUser getUserById(String userId) {
        return userRepository.findById(userId);
    }

    public Chat getChatById(String chatId) {
        return chatRepository.findById(chatId);
    }

    public AbstractUser getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public AbstractUser checkLoginCredentials(String username, String password) {
        AbstractUser user = getUserByUsername(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public AbstractUser writeNewUser(String username, String password, String firstname, String lastname,
                                      boolean isDisabled, boolean isAdmin) {
        if (getUserByUsername(username) != null) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        String hashedPassword = passwordEncoder.encode(password);
        AbstractUser user = isAdmin
                ? new ITAdmin(username, hashedPassword, firstname, lastname, isDisabled, isAdmin)
                : new User(username, hashedPassword, firstname, lastname, isDisabled, isAdmin);
        userRepository.insert(user);
        return user;
    }

    public Chat writeNewChat(String ownerId, String roomName, ArrayList<String> chatterIds, boolean isPrivate) {
        AbstractUser owner = getUserById(ownerId);
        if (owner == null) {
            throw new IllegalArgumentException("No such owner: " + ownerId);
        }
        List<AbstractUser> chatters = new ArrayList<>();
        chatters.add(owner);
        for (String id : chatterIds) {
            AbstractUser chatter = getUserById(id);
            if (chatter != null && !chatters.contains(chatter)) {
                chatters.add(chatter);
            }
        }
        Chat chat = new Chat(owner, roomName, chatters, isPrivate);
        chatRepository.insert(chat);
        return chat;
    }

    public Message writeNewMessage(String content, String authorId, String chatId) {
        AbstractUser author = getUserById(authorId);
        Chat chat = getChatById(chatId);
        if (author == null || chat == null) {
            throw new IllegalArgumentException("Invalid author or chat for message");
        }
        if (!chat.getChatters().contains(author)) {
            throw new SecurityException("User " + authorId + " is not a member of chat " + chatId);
        }
        Message message = new Message(content, author, chat);
        messageRepository.insert(message);
        return message;
    }

    public AbstractUser updateUserIsDisabled(String userId, boolean isDisabled) {
        AbstractUser user = getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("No such user: " + userId);
        }
        user.updateIsDisabled(isDisabled);
        userRepository.update(user);
        return user;
    }

    /**
     * Full profile edit for the IT-admin "edit user" screen: username, name, disabled/admin
     * flags, and optionally the password. Pass null or empty for newPassword to leave it as-is.
     */
    public AbstractUser updateUserDetails(String userId, String username, String firstName, String lastName,
                                           boolean isDisabled, boolean isAdmin, String newPassword) {
        AbstractUser user = getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("No such user: " + userId);
        }
        if (!user.getUserName().equals(username)) {
            AbstractUser existing = getUserByUsername(username);
            if (existing != null && !existing.getId().equals(userId)) {
                throw new IllegalArgumentException("Username already taken: " + username);
            }
        }
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.updateIsDisabled(isDisabled);
        user.setAdmin(isAdmin);
        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        userRepository.update(user);
        return user;
    }

    public Chat addUserToChat(String userId, String chatId, String packetSenderUserId) {
        Chat chat = getChatById(chatId);
        AbstractUser userToAdd = getUserById(userId);
        if (chat == null || userToAdd == null) {
            throw new IllegalArgumentException("Invalid user or chat");
        }
        assertCanManageChat(chat, packetSenderUserId);
        chat.addChatter(userToAdd);
        chatRepository.addMember(chatId, userId);
        return chat;
    }

    public Chat removeUserFromChat(String userId, String chatId, String packetSenderUserId) {
        Chat chat = getChatById(chatId);
        AbstractUser userToRemove = getUserById(userId);
        if (chat == null || userToRemove == null) {
            throw new IllegalArgumentException("Invalid user or chat");
        }
        assertCanManageChat(chat, packetSenderUserId);
        chat.removeChatter(userToRemove);
        chatRepository.removeMember(chatId, userId);
        return chat;
    }

    public Chat renameChat(String senderId, String chatId, String newChatRoomName) {
        Chat chat = getChatById(chatId);
        if (chat == null) {
            throw new IllegalArgumentException("No such chat: " + chatId);
        }
        assertCanManageChat(chat, senderId);
        chat.setRoomName(newChatRoomName);
        chatRepository.rename(chatId, newChatRoomName);
        return chat;
    }

    private void assertCanManageChat(Chat chat, String requesterId) {
        AbstractUser requester = getUserById(requesterId);
        if (requester == null) {
            throw new SecurityException("Unknown requester: " + requesterId);
        }
        if (!chat.getOwner().getId().equals(requesterId) && !requester.isAdmin()) {
            throw new SecurityException("Only the chat owner or an IT admin may manage chat " + chat.getId());
        }
    }
}
