package api.websocket;

import api.security.ChatRelayUserDetails;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Copies the id of the already-authenticated HTTP session's user into the WebSocket session
 * attributes, since the handshake is still a normal (session-cookie-carrying) HTTP request before
 * it's upgraded -- SecurityContextHolder reflects who's logged in at this point. Rejects the
 * handshake outright if nobody's logged in.
 */
public class PrincipalHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof ChatRelayUserDetails details) {
            attributes.put("userId", details.getUser().getId());
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
