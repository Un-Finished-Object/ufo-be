package com.ufo.ufo.domain.chat.dto.response;

import com.ufo.ufo.domain.chat.domain.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageItemResponse(
        Long messageId,
        String text,
        LocalDateTime createdAt
) {
    public static ChatMessageItemResponse from(ChatMessage chatMessage) {
        return new ChatMessageItemResponse(
                chatMessage.getId(),
                chatMessage.getText(),
                chatMessage.getCreatedAt()
        );
    }
}
