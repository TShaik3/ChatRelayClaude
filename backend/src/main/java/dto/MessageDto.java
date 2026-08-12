package dto;

import model.Message;

/** Client-facing shape for a message, for the Phase 3 REST/WebSocket layer. */
public record MessageDto(String id, long createdAt, String content, String authorId, String chatId) {

    public static MessageDto from(Message message) {
        return new MessageDto(message.getId(), message.getCreatedAt(), message.getContent(),
                message.getSender().getId(), message.getChat().getId());
    }
}
