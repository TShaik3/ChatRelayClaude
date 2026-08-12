package api;

import api.security.ChatRelayUserDetails;
import dto.ChatDto;
import model.Chat;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import server.DBManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces CREATE_CHAT/RENAME_CHAT/ADD_USER_TO_CHAT/REMOVE_USER_FROM_CHAT from the socket
 * protocol (Server.java). Ownership/admin authorization for rename and membership changes stays
 * in DBManager (assertCanManageChat) rather than being duplicated here as @PreAuthorize, since
 * it depends on chat data (who owns it), not just the caller's role.
 */
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final DBManager dbManager;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(DBManager dbManager, SimpMessagingTemplate messagingTemplate) {
        this.dbManager = dbManager;
        this.messagingTemplate = messagingTemplate;
    }

    public record CreateChatRequest(List<String> otherUserIds, String roomName, boolean isPrivate) {
    }

    public record RenameChatRequest(String roomName) {
    }

    public record AddMemberRequest(String userId) {
    }

    /** IT admins see every chat, membership aside, for moderation purposes (mirrors DBManager.listChatsVisibleTo). */
    @GetMapping
    public List<ChatDto> getAll(@AuthenticationPrincipal ChatRelayUserDetails principal) {
        return dbManager.listChatsVisibleTo(principal.getUser()).stream().map(ChatDto::from).toList();
    }

    @PostMapping
    public ChatDto create(@AuthenticationPrincipal ChatRelayUserDetails principal, @RequestBody CreateChatRequest request) {
        Chat chat = dbManager.writeNewChat(principal.getUser().getId(), request.roomName(),
                new ArrayList<>(request.otherUserIds()), request.isPrivate());
        ChatDto dto = ChatDto.from(chat);
        for (String memberId : chat.getChattersIds()) {
            messagingTemplate.convertAndSendToUser(memberId, "/queue/updates",
                    Map.of("type", "CHAT_CREATED", "chat", dto));
        }
        return dto;
    }

    @PutMapping("/{id}/rename")
    public ChatDto rename(@AuthenticationPrincipal ChatRelayUserDetails principal, @PathVariable String id,
                           @RequestBody RenameChatRequest request) {
        Chat chat = dbManager.renameChat(principal.getUser().getId(), id, request.roomName());
        ChatDto dto = ChatDto.from(chat);
        messagingTemplate.convertAndSend("/topic/chats/" + id, Map.of("type", "CHAT_RENAMED", "chat", dto));
        return dto;
    }

    @PostMapping("/{id}/members")
    public ChatDto addMember(@AuthenticationPrincipal ChatRelayUserDetails principal, @PathVariable String id,
                              @RequestBody AddMemberRequest request) {
        Chat chat = dbManager.addUserToChat(request.userId(), id, principal.getUser().getId());
        ChatDto dto = ChatDto.from(chat);
        messagingTemplate.convertAndSend("/topic/chats/" + id, Map.of("type", "MEMBER_ADDED", "chat", dto));
        // The new member likely isn't subscribed to /topic/chats/{id} yet -- they didn't know it
        // existed until now -- so they need a direct nudge to pick it up, mirroring the socket
        // protocol's backfill send in Server.handleAddUserToChat.
        messagingTemplate.convertAndSendToUser(request.userId(), "/queue/updates",
                Map.of("type", "CHAT_CREATED", "chat", dto));
        return dto;
    }

    @DeleteMapping("/{id}/members/{userId}")
    public void removeMember(@AuthenticationPrincipal ChatRelayUserDetails principal, @PathVariable String id,
                              @PathVariable String userId) {
        dbManager.removeUserFromChat(userId, id, principal.getUser().getId());
        messagingTemplate.convertAndSend("/topic/chats/" + id,
                Map.of("type", "MEMBER_REMOVED", "chatId", id, "userId", userId));
        messagingTemplate.convertAndSendToUser(userId, "/queue/updates",
                Map.of("type", "REMOVED_FROM_CHAT", "chatId", id));
    }
}
