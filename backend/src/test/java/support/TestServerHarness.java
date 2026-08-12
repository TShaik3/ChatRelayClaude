package support;

import server.Server;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Boots a real Server against a DataSource (see TestDatabase for the isolated-per-test-schema
 * factory) on an OS-assigned ephemeral port, for integration tests to connect real sockets to.
 * Always pair with try-with-resources or an explicit close() in an @AfterEach so the accept-loop
 * thread and any open sockets don't leak between tests.
 */
public class TestServerHarness implements AutoCloseable {

    private final Server server;
    private final Thread serverThread;
    private final int port;
    private final List<TestConnection> openConnections = new CopyOnWriteArrayList<>();

    public TestServerHarness(DataSource dataSource) throws InterruptedException {
        this.server = new Server(0, "127.0.0.1", dataSource);
        AtomicReference<IOException> failure = new AtomicReference<>();
        this.serverThread = new Thread(() -> {
            try {
                server.connect();
            } catch (IOException e) {
                failure.set(e);
            }
        }, "test-server-accept-loop");
        serverThread.setDaemon(true);
        serverThread.start();
        this.port = server.awaitBoundPort();
        if (failure.get() != null) {
            throw new IllegalStateException("Server failed to start", failure.get());
        }
    }

    public Server server() {
        return server;
    }

    public int port() {
        return port;
    }

    public TestConnection connect() throws IOException {
        TestConnection connection = new TestConnection("127.0.0.1", port);
        openConnections.add(connection);
        return connection;
    }

    @Override
    public void close() {
        for (TestConnection connection : openConnections) {
            connection.close();
        }
        server.stop();
        try {
            serverThread.join(2000);
        } catch (InterruptedException ignored) {
        }
    }
}
