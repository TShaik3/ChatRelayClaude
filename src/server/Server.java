package server;

import model.AbstractUser;
import model.Chat;
import model.Message;
import packet.ActionType;
import packet.Packet;
import packet.Status;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Multithreaded chat server: one accept loop handing each new connection
 * off to its own ClientHandler thread, plus the business logic that turns
 * incoming Packets into DBManager calls and outgoing broadcasts.
 */
public class Server {

    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final DBManager dbManager;
    private final int port;
    private final String IP;
    private volatile ServerSocket serverSocket;
    private final CountDownLatch boundLatch = new CountDownLatch(1);

    public Server(int port, String IP) {
        this(port, IP, "./dbFiles/development");
    }

    /** Lets tests point the DB at an isolated, throwaway directory instead of the default. */
    public Server(int port, String IP, String dbFilePath) {
        this.port = port;
        this.IP = IP;
        this.dbManager = new DBManager(dbFilePath, "Users.txt", "Chats.txt", "Messages.txt");
        seedDefaultAdmin();
    }

    private void seedDefaultAdmin() {
        if (dbManager.fetchAllUsers().isEmpty()) {
            dbManager.writeNewUser("admin", "admin", "Admin", "User", false, true);
            System.out.println("No users found; created default admin (username=admin, password=admin)");
        }
    }

    public void connect() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName(IP));
        boundLatch.countDown();
        System.out.println("Server listening on " + IP + ":" + serverSocket.getLocalPort());
        while (!serverSocket.isClosed()) {
            Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    break; // stop() was called
                }
                throw e;
            }
            System.out.println("New connection from " + clientSocket.getRemoteSocketAddress());
            try {
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            } catch (IOException e) {
                System.err.println("Failed to set up client handler: " + e.getMessage());
                clientSocket.close();
            }
        }
    }

    /** Blocks until connect() has bound its socket, then returns the actual listening port (useful with port 0). */
    public int awaitBoundPort() throws InterruptedException {
        boundLatch.await();
        return serverSocket.getLocalPort();
    }

    /** Stops accepting new connections; existing ClientHandler threads exit once their socket closes. */
    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public void receivePacket(String clientId, Packet packet) {
        try {
            switch (packet.getActionType()) {
                case SEND_MESSAGE -> handleSendMessage(clientId, packet);
                case CREATE_CHAT -> handleCreateChat(clientId, packet);
                case CREATE_USER -> handleCreateUser(clientId, packet);
                case UPDATE_USER -> handleUpdateUser(clientId, packet);
                case ADD_USER_TO_CHAT -> handleAddUserToChat(clientId, packet);
                case REMOVE_USER_FROM_CHAT -> handleRemoveUserFromChat(clientId, packet);
                case RENAME_CHAT -> handleRenameChat(clientId, packet);
                case GET_ALL_USERS -> handleGetAllUsers(clientId);
                case GET_ALL_CHATS -> handleGetAllChats(clientId);
                case GET_ALL_MESSAGES -> handleGetAllMessages(clientId);
                default -> sendErrorMessage(clientId, "Unsupported action: " + packet.getActionType());
            }
        } catch (IllegalArgumentException | SecurityException | IndexOutOfBoundsException e) {
            sendErrorMessage(clientId, e.getMessage());
        }
    }

    private void handleSendMessage(String clientId, Packet packet) {
        ArrayList<String> args = packet.getActionArguments();
        String content = args.get(0);
        String chatId = args.get(1);
        Message message = dbManager.writeNewMessage(content, clientId, chatId);
        sendPacketToUsers(
                new Packet(Status.SUCCESS, ActionType.NEW_MESSAGE_BROADCAST, oneArg(message.toString()), "server"),
                message.getChat().getChattersIds().toArray(new String[0]));
    }

    private void handleCreateChat(String clientId, Packet packet) {
        ArrayList<String> args = packet.getActionArguments();
        List<String> otherUserIds = Arrays.asList(args.get(0).split("/"));
        String chatName = args.get(1);
        boolean isPrivate = Boolean.parseBoolean(args.get(2));
        Chat chat = dbManager.writeNewChat(clientId, chatName, new ArrayList<>(otherUserIds), isPrivate);
        sendPacketToUsers(
                new Packet(Status.SUCCESS, ActionType.NEW_CHAT_BROADCAST, oneArg(chat.toString()), "server"),
                chat.getChattersIds().toArray(new String[0]));
    }

    private void handleCreateUser(String clientId, Packet packet) {
        requireAdmin(clientId);
        ArrayList<String> args = packet.getActionArguments();
        String username = args.get(0);
        String password = args.get(1);
        String firstname = args.get(2);
        String lastname = args.get(3);
        boolean isDisabled = Boolean.parseBoolean(args.get(4));
        boolean isAdmin = Boolean.parseBoolean(args.get(5));
        AbstractUser newUser = dbManager.writeNewUser(username, password, firstname, lastname, isDisabled, isAdmin);
        sendPacketToUsers(
                new Packet(Status.SUCCESS, ActionType.NEW_USER_BROADCAST, oneArg(newUser.toStringClient()), "server"),
                clients.keySet().toArray(new String[0]));
    }

    /** args: [userId, username, password(empty = unchanged), firstname, lastname, isDisabled, isAdmin] */
    private void handleUpdateUser(String clientId, Packet packet) {
        requireAdmin(clientId);
        ArrayList<String> args = packet.getActionArguments();
        String userToUpdateId = args.get(0);
        String username = args.get(1);
        String password = args.get(2);
        String firstname = args.get(3);
        String lastname = args.get(4);
        boolean isDisabled = Boolean.parseBoolean(args.get(5));
        boolean isAdmin = Boolean.parseBoolean(args.get(6));
        AbstractUser updated = dbManager.updateUserDetails(userToUpdateId, username, firstname, lastname,
                isDisabled, isAdmin, password);
        sendPacketToUsers(
                new Packet(Status.SUCCESS, ActionType.UPDATED_USER_BROADCAST, oneArg(updated.toStringClient()), "server"),
                clients.keySet().toArray(new String[0]));
    }

    private void handleAddUserToChat(String clientId, Packet packet) {
        ArrayList<String> args = packet.getActionArguments();
        String userToAddId = args.get(0);
        String chatRoomId = args.get(1);
        Chat chat = dbManager.addUserToChat(userToAddId, chatRoomId, clientId);
        sendPacketToUsers(
                new Packet(Status.SUCCESS, ActionType.ADD_USER_TO_CHAT_BROADCAST,
                        oneArg2(userToAddId, chat.toString()), "server"),
                chat.getChattersIds().toArray(new String[0]));
        ClientHandler addedHandler = clients.get(userToAddId);
        if (addedHandler != null) {
            AbstractUser addedUser = dbManager.getUserById(userToAddId);
            addedHandler.sendPacket(new Packet(Status.SUCCESS, ActionType.GET_ALL_MESSAGES,
                    dbManager.fetchAllMessages(addedUser), "server"));
        }
    }

    private void handleRemoveUserFromChat(String clientId, Packet packet) {
        ArrayList<String> args = packet.getActionArguments();
        String userIdToRemove = args.get(0);
        String chatId = args.get(1);
        Chat chat = dbManager.getChatById(chatId);
        List<String> recipients = chat != null ? new ArrayList<>(chat.getChattersIds()) : new ArrayList<>();
        recipients.add(userIdToRemove);
        dbManager.removeUserFromChat(userIdToRemove, chatId, clientId);
        sendPacketToUsers(
                new Packet(Status.SUCCESS, ActionType.REMOVE_USER_FROM_CHAT_BROADCAST,
                        oneArg2(userIdToRemove, chatId), "server"),
                recipients.toArray(new String[0]));
    }

    private void handleRenameChat(String clientId, Packet packet) {
        ArrayList<String> args = packet.getActionArguments();
        String chatId = args.get(0);
        String newChatRoomName = args.get(1);
        Chat chat = dbManager.renameChat(clientId, chatId, newChatRoomName);
        sendPacketToUsers(
                new Packet(Status.SUCCESS, ActionType.RENAME_CHAT_BROADCAST,
                        oneArg2(chatId, newChatRoomName), "server"),
                chat.getChattersIds().toArray(new String[0]));
    }

    private void handleGetAllUsers(String clientId) {
        sendPacketToUsers(new Packet(Status.SUCCESS, ActionType.GET_ALL_USERS, dbManager.fetchAllUsers(), "server"),
                new String[]{clientId});
    }

    private void handleGetAllChats(String clientId) {
        AbstractUser user = dbManager.getUserById(clientId);
        sendPacketToUsers(new Packet(Status.SUCCESS, ActionType.GET_ALL_CHATS, dbManager.fetchAllChats(user), "server"),
                new String[]{clientId});
    }

    private void handleGetAllMessages(String clientId) {
        AbstractUser user = dbManager.getUserById(clientId);
        sendPacketToUsers(new Packet(Status.SUCCESS, ActionType.GET_ALL_MESSAGES, dbManager.fetchAllMessages(user), "server"),
                new String[]{clientId});
    }

    private void requireAdmin(String clientId) {
        AbstractUser user = dbManager.getUserById(clientId);
        if (user == null || !user.isAdmin()) {
            throw new SecurityException("Only an IT admin may perform this action");
        }
    }

    private ArrayList<String> oneArg(String value) {
        ArrayList<String> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    private ArrayList<String> oneArg2(String a, String b) {
        ArrayList<String> list = new ArrayList<>();
        list.add(a);
        list.add(b);
        return list;
    }

    public void handleLogout(String clientId) {
        if (clientId != null) {
            clients.remove(clientId);
        }
    }

    public void sendErrorMessage(String userId, String errorMessage) {
        ClientHandler handler = clients.get(userId);
        if (handler != null) {
            handler.sendPacket(new Packet(Status.ERROR, ActionType.ERROR, oneArg(errorMessage), "server"));
        }
    }

    public void sendSuccessMessage(String userId, String successMessage) {
        ClientHandler handler = clients.get(userId);
        if (handler != null) {
            handler.sendPacket(new Packet(Status.SUCCESS, ActionType.SUCCESS, oneArg(successMessage), "server"));
        }
    }

    public void sendPacketToUsers(Packet packet, String[] userIds) {
        for (String userId : userIds) {
            ClientHandler handler = clients.get(userId);
            if (handler != null) {
                handler.sendPacket(packet);
            }
        }
    }

    public DBManager getDBManager() {
        return dbManager;
    }

    public void addClient(String userId, ClientHandler ch) {
        clients.put(userId, ch);
    }

    public boolean containsClient(String userId) {
        return clients.containsKey(userId);
    }
}
