package server;

import model.AbstractUser;
import packet.ActionType;
import packet.Packet;
import packet.Status;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

/**
 * Owns one client connection. Runs on its own thread (handed off from
 * Server's accept loop) reading Packets until the socket closes.
 * Login is handled entirely here since the handler has no userId to be
 * looked up by until it succeeds; every other action is delegated to
 * Server.receivePacket once this handler is registered under a userId.
 */
public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private String userId;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private final Server server;

    public ClientHandler(Socket socket, Server server) throws IOException {
        this.clientSocket = socket;
        this.server = server;
        // Output stream must be created (and flushed) before the input stream
        // on both ends of a socket pair, or ObjectInputStream construction blocks forever.
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.outputStream.flush();
        this.inputStream = new ObjectInputStream(socket.getInputStream());
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public ObjectInputStream getInputStream() {
        return inputStream;
    }

    public ObjectOutputStream getOutputStream() {
        return outputStream;
    }

    public synchronized void sendPacket(Packet packet) {
        try {
            outputStream.writeObject(packet);
            outputStream.flush();
            outputStream.reset();
        } catch (IOException e) {
            System.err.println("Failed to send packet to " + userId + ": " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                Packet packet = (Packet) inputStream.readObject();
                try {
                    if (packet.getActionType() == ActionType.LOGIN) {
                        handleLogin(packet);
                    } else if (userId == null) {
                        // Not authenticated yet: reply directly rather than routing through
                        // Server, which keys its client registry by userId and would NPE on null.
                        sendPacket(new Packet(Status.ERROR, ActionType.ERROR, listOf("You must log in first"), "server"));
                    } else if (packet.getActionType() == ActionType.LOGOUT) {
                        // Deauthenticate this connection but keep the socket open -- the same
                        // client process stays connected and can log back in without
                        // reconnecting. Closing the socket here left the client writing into a
                        // dead pipe on its very next send.
                        server.handleLogout(userId);
                        setUserId(null);
                    } else {
                        server.receivePacket(userId, packet);
                    }
                } catch (RuntimeException e) {
                    // Malformed actionArgs (e.g. handleLogin's own args.get(1) on a short list)
                    // must not kill the connection -- report it and keep the session alive.
                    sendPacket(new Packet(Status.ERROR, ActionType.ERROR,
                            listOf("Malformed request: " + e.getMessage()), "server"));
                }
            }
        } catch (EOFException | SocketException e) {
            // client disconnected
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client handler error for " + userId + ": " + e.getMessage());
        } finally {
            server.handleLogout(userId);
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleLogin(Packet packet) {
        ArrayList<String> args = packet.getActionArguments();
        String username = args.get(0);
        String password = args.get(1);

        AbstractUser user = server.getDBManager().checkLoginCredentials(username, password);
        if (user == null || user.isDisabled()) {
            String reason = user == null ? "Invalid login credentials" : "Account disabled";
            sendPacket(new Packet(Status.ERROR, ActionType.ERROR, listOf(reason), "server"));
            return;
        }

        setUserId(user.getId());
        server.addClient(user.getId(), this);

        sendPacket(new Packet(Status.SUCCESS, ActionType.LOGIN,
                listOf(user.getId(), user.getFirstName(), user.getLastName(),
                        String.valueOf(user.isAdmin()), String.valueOf(user.isDisabled())),
                "server"));

        sendPacket(new Packet(Status.SUCCESS, ActionType.GET_ALL_USERS,
                server.getDBManager().fetchAllUsers(), "server"));
        sendPacket(new Packet(Status.SUCCESS, ActionType.GET_ALL_CHATS,
                server.getDBManager().fetchAllChats(user), "server"));
        sendPacket(new Packet(Status.SUCCESS, ActionType.GET_ALL_MESSAGES,
                server.getDBManager().fetchAllMessages(user), "server"));
    }

    private ArrayList<String> listOf(String... values) {
        ArrayList<String> list = new ArrayList<>();
        for (String v : values) list.add(v);
        return list;
    }
}
