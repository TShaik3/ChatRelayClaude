package client;

import model.AbstractUser;
import model.Chat;
import model.ITAdmin;
import model.Message;
import model.User;
import packet.ActionType;
import packet.Packet;
import packet.Status;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side connection and state. Sends request Packets to the server and,
 * via ClientListener, receives replies/broadcasts which are folded into the
 * local users/chats model before the GUI is refreshed.
 */
public class Client {

    private Socket socket;
    private boolean isConnected;
    private final String targetIP;
    private final String targetPort;
    private String username;
    private Chat lastChatSent;
    private final List<Chat> chats = new ArrayList<>();
    private final List<AbstractUser> users = new ArrayList<>();
    private final Map<String, Chat> chatsById = new HashMap<>();
    private final Map<String, AbstractUser> usersById = new HashMap<>();
    private String userId;
    private boolean isITAdmin;
    private AbstractUser thisUser;
    private ObjectOutputStream objectStream;
    private ObjectInputStream objectInStream;
    private Thread input;
    private GUI clientGUI;

    public Client(String targetIP, String targetPort) {
        this.targetIP = targetIP;
        this.targetPort = targetPort;
    }

    public void startUp() throws IOException {
        socket = new Socket(targetIP, Integer.parseInt(targetPort));
        objectStream = new ObjectOutputStream(socket.getOutputStream());
        objectStream.flush();
        objectInStream = new ObjectInputStream(socket.getInputStream());
        isConnected = true;

        input = new Thread(new ClientListener(this, objectInStream));
        input.setDaemon(true);
        input.start();

        clientGUI = new GUI(this);
        SwingUtilities.invokeLater(clientGUI::run);
    }

    private void send(ActionType action, ArrayList<String> args) {
        Packet packet = new Packet(Status.NONE, action, args, userId);
        try {
            synchronized (objectStream) {
                objectStream.writeObject(packet);
                objectStream.flush();
                objectStream.reset();
            }
        } catch (IOException e) {
            System.err.println("Failed to send packet: " + e.getMessage());
        }
    }

    public void login(String username, String password) {
        this.username = username;
        send(ActionType.LOGIN, listOf(username, password));
    }

    public void sendMessage(String chatId, String content) {
        send(ActionType.SEND_MESSAGE, listOf(content, chatId));
    }

    public void createChat(String[] userIds, String chatName, boolean isPrivate) {
        send(ActionType.CREATE_CHAT, listOf(String.join("/", userIds), chatName, String.valueOf(isPrivate)));
    }

    public void createUser(String username, String password, String firstname, String lastname, boolean isAdmin) {
        send(ActionType.CREATE_USER, listOf(username, password, firstname, lastname, "false", String.valueOf(isAdmin)));
    }

    /** Pass an empty string for newPassword to leave the existing password unchanged. */
    public void updateUser(String userId, String username, String firstname, String lastname,
                            boolean isDisabled, boolean isAdmin, String newPassword) {
        send(ActionType.UPDATE_USER,
                listOf(userId, username, newPassword == null ? "" : newPassword, firstname, lastname,
                        String.valueOf(isDisabled), String.valueOf(isAdmin)));
    }

    public void addUserToChat(String userId, String chatId) {
        send(ActionType.ADD_USER_TO_CHAT, listOf(userId, chatId));
    }

    public void removeUserFromChat(String userId, String chatId) {
        send(ActionType.REMOVE_USER_FROM_CHAT, listOf(userId, chatId));
    }

    public void renameChat(String chatId, String chatName) {
        send(ActionType.RENAME_CHAT, listOf(chatId, chatName));
    }

    public void saveChatToTxt(Chat chat) {
        saveChatToTxt(chat, new File("chat-" + chat.getId() + "-export.txt"));
    }

    public void saveChatToTxt(Chat chat, File destination) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(destination))) {
            writer.println("Chat: " + chat.getRoomName());
            for (Message message : chat.getMessages()) {
                String authorName = message.getSender() != null ? message.getSender().getUserName() : "unknown";
                writer.println("[" + message.getCreatedAt() + "] " + authorName + ": " + message.getContent());
            }
        } catch (IOException e) {
            System.err.println("Failed to export chat: " + e.getMessage());
        }
    }

    public void logout() {
        send(ActionType.LOGOUT, new ArrayList<>());

        userId = null;
        thisUser = null;
        isITAdmin = false;
        lastChatSent = null;
        chats.clear();
        users.clear();
        chatsById.clear();
        usersById.clear();

        updateState(ActionType.LOGOUT);
    }

    public void getAllUsers() {
        send(ActionType.GET_ALL_USERS, new ArrayList<>());
    }

    public void getAllChats() {
        send(ActionType.GET_ALL_CHATS, new ArrayList<>());
    }

    /** Folds an incoming Packet's payload into local state, then refreshes the GUI. */
    void handleIncomingPacket(Packet packet) {
        ArrayList<String> args = packet.getActionArguments();
        ActionType action = packet.getActionType();

        switch (action) {
            case LOGIN -> {
                if (packet.getStatus() == Status.SUCCESS) {
                    userId = args.get(0);
                    isITAdmin = Boolean.parseBoolean(args.get(3));
                    boolean isDisabled = Boolean.parseBoolean(args.get(4));
                    thisUser = isITAdmin
                            ? new ITAdmin(true, userId, username, args.get(1), args.get(2), isDisabled, true)
                            : new User(true, userId, username, args.get(1), args.get(2), isDisabled, false);
                    addOrReplaceUser(thisUser);
                }
            }
            case ERROR -> { /* surfaced via GUI.update -> showMessageDialog */ }
            case GET_ALL_USERS -> {
                for (String entry : args) {
                    addOrReplaceUser(parseUser(entry));
                }
            }
            case NEW_USER_BROADCAST -> addOrReplaceUser(parseUser(args.get(0)));
            case UPDATED_USER_BROADCAST -> {
                AbstractUser updated = parseUser(args.get(0));
                addOrReplaceUser(updated);
                // addOrReplaceUser swaps in a fresh object under usersById/users; thisUser is a
                // separate field pointing at the old one, so it goes stale unless refreshed too
                // (e.g. an admin editing their own profile, or another admin editing them).
                if (updated.getId().equals(userId)) {
                    thisUser = updated;
                    isITAdmin = updated.isAdmin();
                    username = updated.getUserName();
                }
            }
            case GET_ALL_CHATS -> {
                for (String entry : args) {
                    addOrReplaceChat(parseChat(entry));
                }
            }
            case NEW_CHAT_BROADCAST -> {
                Chat chat = parseChat(args.get(0));
                addOrReplaceChat(chat);
                lastChatSent = chat;
            }
            case ADD_USER_TO_CHAT_BROADCAST -> addOrReplaceChat(parseChat(args.get(1)));
            case REMOVE_USER_FROM_CHAT_BROADCAST -> {
                Chat chat = chatsById.get(args.get(1));
                AbstractUser removed = usersById.get(args.get(0));
                if (chat != null && removed != null) {
                    chat.removeChatter(removed);
                }
            }
            case RENAME_CHAT_BROADCAST -> {
                Chat chat = chatsById.get(args.get(0));
                if (chat != null) {
                    chat.setRoomName(args.get(1));
                }
            }
            case GET_ALL_MESSAGES -> {
                for (String entry : args) {
                    parseAndStoreMessage(entry);
                }
            }
            case NEW_MESSAGE_BROADCAST -> {
                Message message = parseAndStoreMessage(args.get(0));
                if (message != null) {
                    lastChatSent = message.getChat();
                }
            }
            default -> { }
        }

        updateState(action);
    }

    void handleDisconnect() {
        isConnected = false;
    }

    private void updateState(ActionType action) {
        if (clientGUI != null) {
            SwingUtilities.invokeLater(() -> clientGUI.update(action));
        }
    }

    private AbstractUser parseUser(String entry) {
        String[] parts = entry.split("/");
        String id = parts[0];
        String username = Packet.unsanitize(parts[1]);
        String firstName = Packet.unsanitize(parts[2]);
        String lastName = Packet.unsanitize(parts[3]);
        boolean isDisabled = Boolean.parseBoolean(parts[4]);
        boolean isAdmin = Boolean.parseBoolean(parts[5]);
        return isAdmin
                ? new ITAdmin(true, id, username, firstName, lastName, isDisabled, isAdmin)
                : new User(true, id, username, firstName, lastName, isDisabled, isAdmin);
    }

    private Chat parseChat(String entry) {
        String[] parts = entry.split("/");
        String id = parts[0];
        String ownerId = parts[1];
        String roomName = Packet.unsanitize(parts[2]);
        boolean isPrivate = Boolean.parseBoolean(parts[3]);
        List<AbstractUser> chatters = new ArrayList<>();
        if (parts.length > 4 && !parts[4].isEmpty()) {
            for (String chatterId : parts[4].split(",")) {
                AbstractUser chatter = usersById.get(chatterId);
                if (chatter != null) chatters.add(chatter);
            }
        }
        AbstractUser owner = usersById.get(ownerId);
        return new Chat(owner, roomName, id, chatters, isPrivate);
    }

    private Message parseAndStoreMessage(String entry) {
        String[] parts = entry.split("/");
        String id = parts[0];
        long createdAt = Long.parseLong(parts[1]);
        String content = Packet.unsanitize(parts[2]);
        AbstractUser author = usersById.get(parts[3]);
        Chat chat = chatsById.get(parts[4]);
        if (author == null || chat == null) return null;
        Message message = new Message(id, createdAt, content, author, chat);
        chat.addMessage(message);
        return message;
    }

    private void addOrReplaceUser(AbstractUser user) {
        usersById.put(user.getId(), user);
        users.removeIf(u -> u.getId().equals(user.getId()));
        users.add(user);
    }

    private void addOrReplaceChat(Chat chat) {
        chatsById.put(chat.getId(), chat);
        chats.removeIf(c -> c.getId().equals(chat.getId()));
        chats.add(chat);
    }

    private ArrayList<String> listOf(String... values) {
        ArrayList<String> list = new ArrayList<>();
        for (String v : values) list.add(v);
        return list;
    }

    public boolean getIsConnected() {
        return isConnected;
    }

    public String getThisUserId() {
        return userId;
    }

    public AbstractUser getThisUser() {
        return thisUser;
    }

    public boolean getAdminStatus() {
        return isITAdmin;
    }

    public List<Chat> getChats() {
        return chats;
    }

    public List<AbstractUser> getUsers() {
        return users;
    }

    public Chat getLastChatSent() {
        return lastChatSent;
    }
}
