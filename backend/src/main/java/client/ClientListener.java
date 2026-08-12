package client;

import packet.Packet;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;

/**
 * Background thread that continuously reads Packets pushed by the server
 * (broadcasts, replies) and hands each one to Client for processing.
 */
public class ClientListener implements Runnable {

    private final Client client;
    private final ObjectInputStream inputStream;

    public ClientListener(Client client, ObjectInputStream inputStream) {
        this.client = client;
        this.inputStream = inputStream;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Packet packet = (Packet) inputStream.readObject();
                client.handleIncomingPacket(packet);
            }
        } catch (EOFException | SocketException e) {
            client.handleDisconnect();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client listener error: " + e.getMessage());
            client.handleDisconnect();
        }
    }
}
