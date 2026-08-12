package dto;

import model.Chat;

import java.util.List;

/** Client-facing shape for a chat, for the Phase 3 REST/WebSocket layer. */
public record ChatDto(String id, String ownerId, String roomName, boolean isPrivate, List<String> chatterIds) {

    public static ChatDto from(Chat chat) {
        return new ChatDto(chat.getId(), chat.getOwner().getId(), chat.getRoomName(), chat.isPrivate(),
                chat.getChattersIds());
    }
}
