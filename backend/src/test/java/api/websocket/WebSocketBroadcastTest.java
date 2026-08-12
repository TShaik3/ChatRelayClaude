package api.websocket;

import app.BackendApplication;
import model.AbstractUser;
import model.Chat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import server.DBManager;
import support.ApiSession;
import support.TestDatabase;
import support.TestRestTemplates;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Proves the realtime replacement for Server.sendPacketToUsers/ClientHandler.sendPacket actually
 * works end-to-end: a session-cookie-authenticated STOMP client subscribes to a chat's topic, a
 * REST call sends a message into that chat, and the broadcast arrives over the WebSocket -- not
 * just that the HTTP side of MessageController.send() returns 200.
 */
@SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketBroadcastTest {

    private static final TestDatabase TEST_DB = TestDatabase.createSchema();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TEST_DB::jdbcUrl);
        registry.add("spring.datasource.username", TEST_DB::username);
        registry.add("spring.datasource.password", TEST_DB::password);
    }

    @AfterAll
    static void tearDown() {
        TEST_DB.close();
    }

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;

    @Autowired
    private DBManager dbManager;

    @BeforeEach
    void setUpRestTemplate() {
        restTemplate = TestRestTemplates.create(port);
    }

    @Test
    void newMessageBroadcastsToChatTopicSubscribers() throws Exception {
        AbstractUser member = dbManager.writeNewUser("wsmember-" + System.nanoTime(), "pw", "Ws", "Member", false, false);
        Chat chat = dbManager.writeNewChat(member.getId(), "ws-room", new ArrayList<>(), false);

        ApiSession session = new ApiSession(restTemplate);
        session.login(member.getUserName(), "pw");
        assertNotNull(session.sessionCookie(), "must be logged in before the WebSocket handshake can authenticate");

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, session.sessionCookie());

        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession stompSession, StompHeaders connectedHeaders) {
                stompSession.subscribe("/topic/chats/" + chat.getId(), new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void handleFrame(StompHeaders headers, Object payload) {
                        received.add((Map<String, Object>) payload);
                    }
                });
            }
        };

        StompSession stompSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, handler)
                .get(5, TimeUnit.SECONDS);
        try {
            // Subscription is asynchronous over the wire; give the broker a moment to register it
            // server-side before triggering the broadcast, or the message could be sent before
            // anyone's listening.
            Thread.sleep(300);

            session.post("/api/chats/" + chat.getId() + "/messages", Map.of("content", "ws hello"), Map.class);

            Map<String, Object> event = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(event, "expected a broadcast on /topic/chats/{id} after sending a message");
            assertEquals("NEW_MESSAGE", event.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> messagePayload = (Map<String, Object>) event.get("message");
            assertEquals("ws hello", messagePayload.get("content"));
        } finally {
            stompSession.disconnect();
        }
    }
}
