package support;

import packet.ActionType;
import packet.Packet;
import packet.Status;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

/**
 * One client connection to a test server: thin wrapper around the raw
 * ObjectOutputStream/ObjectInputStream protocol so integration tests can
 * send/receive real Packets without going through Client or the GUI.
 */
public class TestConnection implements AutoCloseable {

    /** Generous but finite: a hung recv() should fail the test, not the whole suite. */
    private static final int READ_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public TestConnection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    public static ArrayList<String> args(String... values) {
        ArrayList<String> list = new ArrayList<>();
        for (String v : values) list.add(v);
        return list;
    }

    public void send(Status status, ActionType type, ArrayList<String> args, String senderId) throws IOException {
        out.writeObject(new Packet(status, type, args, senderId));
        out.flush();
        out.reset();
    }

    public void sendRaw(Object notAPacket) throws IOException {
        out.writeObject(notAPacket);
        out.flush();
        out.reset();
    }

    public Packet recv() throws IOException, ClassNotFoundException {
        return (Packet) in.readObject();
    }

    /** Logs in and drains the standard 4-packet login sequence, returning the assigned userId. */
    public String login(String username, String password) throws IOException, ClassNotFoundException {
        send(Status.NONE, ActionType.LOGIN, args(username, password), null);
        Packet loginReply = recv();
        if (loginReply.getStatus() != Status.SUCCESS) {
            throw new IllegalStateException("Login failed: " + loginReply.getActionArguments());
        }
        recv(); // GET_ALL_USERS
        recv(); // GET_ALL_CHATS
        recv(); // GET_ALL_MESSAGES
        return loginReply.getActionArguments().get(0);
    }

    public Socket rawSocket() {
        return socket;
    }

    /** Shortens the read timeout for a "confirm nothing arrives" check, instead of eating the full default wait. */
    public void setReadTimeoutMs(int millis) throws IOException {
        socket.setSoTimeout(millis);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
