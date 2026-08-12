package server;

import model.AbstractUser;
import model.Chat;
import model.ITAdmin;
import model.Message;
import model.User;
import packet.Packet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-backed persistence and business logic for users, chats and messages.
 * Data lives in plain "/"-delimited text files under txtFilePath; the maps
 * held in memory are the source of truth while the server is running, and
 * are rewritten to disk on every mutation.
 */
public class DBManager {

    private final ConcurrentHashMap<String, AbstractUser> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Chat> chats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Message> messages = new ConcurrentHashMap<>();

    private final String txtFilePath;
    private final String userTxtFilename;
    private final String chatTxtFilename;
    private final String messageTxtFilename;

    private final Object fileLock = new Object();

    public DBManager(String filepath, String userTxtFilename, String chatTxtFilename, String messageTxtFilename) {
        this.txtFilePath = filepath;
        this.userTxtFilename = userTxtFilename;
        this.chatTxtFilename = chatTxtFilename;
        this.messageTxtFilename = messageTxtFilename;

        new File(txtFilePath).mkdirs();
        loadUsers();
        loadChats();
        loadMessages();
    }

    private File userFile() {
        return new File(txtFilePath, userTxtFilename);
    }

    private File chatFile() {
        return new File(txtFilePath, chatTxtFilename);
    }

    private File messageFile() {
        return new File(txtFilePath, messageTxtFilename);
    }

    private void loadUsers() {
        File file = userFile();
        if (!file.exists()) return;
        int highest = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("/");
                String username = Packet.unsanitize(parts[0]);
                String password = Packet.unsanitize(parts[1]);
                String id = parts[2];
                String firstName = Packet.unsanitize(parts[3]);
                String lastName = Packet.unsanitize(parts[4]);
                boolean isDisabled = Boolean.parseBoolean(parts[5]);
                boolean isAdmin = Boolean.parseBoolean(parts[6]);

                AbstractUser user = isAdmin
                        ? new ITAdmin(username, password, id, firstName, lastName, isDisabled, isAdmin)
                        : new User(username, password, id, firstName, lastName, isDisabled, isAdmin);
                users.put(id, user);
                highest = Math.max(highest, Integer.parseInt(id));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load users from " + file, e);
        }
        if (highest >= 0) {
            AbstractUser.restoreCount(highest);
        }
    }

    private void loadChats() {
        File file = chatFile();
        if (!file.exists()) return;
        int highest = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("/");
                String id = parts[0];
                String ownerId = parts[1];
                String roomName = Packet.unsanitize(parts[2]);
                boolean isPrivate = Boolean.parseBoolean(parts[3]);
                List<AbstractUser> chatters = new ArrayList<>();
                if (parts.length > 4 && !parts[4].isEmpty()) {
                    for (String chatterId : parts[4].split(",")) {
                        AbstractUser chatter = users.get(chatterId);
                        if (chatter != null) chatters.add(chatter);
                    }
                }
                AbstractUser owner = users.get(ownerId);
                Chat chat = new Chat(owner, roomName, id, chatters, isPrivate);
                chats.put(id, chat);
                highest = Math.max(highest, Integer.parseInt(id));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load chats from " + file, e);
        }
        if (highest >= 0) {
            Chat.restoreCount(highest);
        }
    }

    private void loadMessages() {
        File file = messageFile();
        if (!file.exists()) return;
        int highest = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("/");
                String id = parts[0];
                long createdAt = Long.parseLong(parts[1]);
                String content = Packet.unsanitize(parts[2]);
                String authorId = parts[3];
                String chatId = parts[4];

                AbstractUser author = users.get(authorId);
                Chat chat = chats.get(chatId);
                if (author == null || chat == null) continue;
                Message message = new Message(id, createdAt, content, author, chat);
                messages.put(id, message);
                chat.addMessage(message);
                highest = Math.max(highest, Integer.parseInt(id));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load messages from " + file, e);
        }
        if (highest >= 0) {
            Message.restoreCount(highest);
        }
    }

    private void rewriteUsersFile() {
        synchronized (fileLock) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(userFile(), false))) {
                for (AbstractUser user : users.values()) {
                    writer.println(user.toString());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to write " + userFile(), e);
            }
        }
    }

    private void rewriteChatsFile() {
        synchronized (fileLock) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(chatFile(), false))) {
                for (Chat chat : chats.values()) {
                    writer.println(chat.toString());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to write " + chatFile(), e);
            }
        }
    }

    private void rewriteMessagesFile() {
        synchronized (fileLock) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(messageFile(), false))) {
                for (Message message : messages.values()) {
                    writer.println(message.toString());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to write " + messageFile(), e);
            }
        }
    }

    public ArrayList<String> fetchAllUsers() {
        ArrayList<String> result = new ArrayList<>();
        for (AbstractUser user : users.values()) {
            result.add(user.toStringClient());
        }
        return result;
    }

    /** IT admins see every chat, membership aside, for moderation purposes. */
    public ArrayList<String> fetchAllChats(AbstractUser user) {
        ArrayList<String> result = new ArrayList<>();
        for (Chat chat : chats.values()) {
            if (user.isAdmin() || chat.getChatters().contains(user)) {
                result.add(chat.toString());
            }
        }
        return result;
    }

    /** IT admins see every message, membership aside, for moderation purposes. */
    public ArrayList<String> fetchAllMessages(AbstractUser user) {
        ArrayList<String> result = new ArrayList<>();
        for (Chat chat : chats.values()) {
            if (user.isAdmin() || chat.getChatters().contains(user)) {
                for (Message message : chat.getMessages()) {
                    result.add(message.toString());
                }
            }
        }
        return result;
    }

    public AbstractUser getUserById(String userId) {
        return users.get(userId);
    }

    public Chat getChatById(String chatId) {
        return chats.get(chatId);
    }

    public AbstractUser getUserByUsername(String username) {
        for (AbstractUser user : users.values()) {
            if (user.getUserName().equals(username)) {
                return user;
            }
        }
        return null;
    }

    public AbstractUser checkLoginCredentials(String username, String password) {
        AbstractUser user = getUserByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public AbstractUser writeNewUser(String username, String password, String firstname, String lastname,
                                      boolean isDisabled, boolean isAdmin) {
        if (getUserByUsername(username) != null) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        AbstractUser user = isAdmin
                ? new ITAdmin(username, password, firstname, lastname, isDisabled, isAdmin)
                : new User(username, password, firstname, lastname, isDisabled, isAdmin);
        users.put(user.getId(), user);
        rewriteUsersFile();
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
        chats.put(chat.getId(), chat);
        rewriteChatsFile();
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
        messages.put(message.getId(), message);
        chat.addMessage(message);
        rewriteMessagesFile();
        return message;
    }

    public AbstractUser updateUserIsDisabled(String userId, boolean isDisabled) {
        AbstractUser user = getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("No such user: " + userId);
        }
        user.updateIsDisabled(isDisabled);
        rewriteUsersFile();
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
            user.setPassword(newPassword);
        }
        rewriteUsersFile();
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
        rewriteChatsFile();
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
        rewriteChatsFile();
        return chat;
    }

    public Chat renameChat(String senderId, String chatId, String newChatRoomName) {
        Chat chat = getChatById(chatId);
        if (chat == null) {
            throw new IllegalArgumentException("No such chat: " + chatId);
        }
        assertCanManageChat(chat, senderId);
        chat.setRoomName(newChatRoomName);
        rewriteChatsFile();
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
