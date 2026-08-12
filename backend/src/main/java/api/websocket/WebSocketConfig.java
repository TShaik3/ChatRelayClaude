package api.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Replaces the socket protocol's server-push broadcasts (Server.sendPacketToUsers,
 * ClientHandler.sendPacket). /topic destinations are chat-scoped broadcasts (see ChatController/
 * MessageController); /user/queue/updates is the per-user channel for events aimed at someone who
 * may not be subscribed to a /topic yet (e.g. just added to a brand new chat).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new PrincipalHandshakeInterceptor())
                .setHandshakeHandler(new UserPrincipalHandshakeHandler());
    }
}
