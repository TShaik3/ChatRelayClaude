package api.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Builds the STOMP session's Principal from the user id PrincipalHandshakeInterceptor stashed in
 * the WebSocket session attributes -- this is what SimpMessagingTemplate.convertAndSendToUser
 * matches against for per-user routing (e.g. "you were just added to a chat").
 */
public class UserPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
        Object userId = attributes.get("userId");
        return userId == null ? null : new StompPrincipal((String) userId);
    }

    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
