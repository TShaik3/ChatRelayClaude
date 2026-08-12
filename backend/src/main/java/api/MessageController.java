package api;

import api.security.ChatRelayUserDetails;
import dto.MessageDto;
import model.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import server.DBManager;

import java.util.List;
import java.util.Map;

/**
 * Replaces SEND_MESSAGE/GET_ALL_MESSAGES from the socket protocol (Server.java) -- scoped to one
 * chat at a time rather than dumping every visible message up front, since the Phase 4 frontend
 * only needs a chat's history once that chat is actually opened.
 */
@RestController
@RequestMapping("/api/chats/{chatId}/messages")
public class MessageController {

    private final DBManager dbManager;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageController(DBManager dbManager, SimpMessagingTemplate messagingTemplate) {
        this.dbManager = dbManager;
        this.messagingTemplate = messagingTemplate;
    }

    public record SendMessageRequest(String content) {
    }

    @GetMapping
    public List<MessageDto> list(@AuthenticationPrincipal ChatRelayUserDetails principal, @PathVariable String chatId) {
        return dbManager.fetchMessagesForChat(principal.getUser(), chatId).stream().map(MessageDto::from).toList();
    }

    @PostMapping
    public MessageDto send(@AuthenticationPrincipal ChatRelayUserDetails principal, @PathVariable String chatId,
                            @RequestBody SendMessageRequest request) {
        Message message = dbManager.writeNewMessage(request.content(), principal.getUser().getId(), chatId);
        MessageDto dto = MessageDto.from(message);
        messagingTemplate.convertAndSend("/topic/chats/" + chatId, Map.of("type", "NEW_MESSAGE", "message", dto));
        return dto;
    }
}
