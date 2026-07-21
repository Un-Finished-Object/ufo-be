package com.ufo.ufo.domain.chat.dto.response;

import com.ufo.ufo.domain.chat.domain.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageItemResponse(
        String senderName,
        Long messageId,
        String text,
        String replySenderName,
        Long replyMessageId,
        LocalDateTime createdAt
) {
    public static ChatMessageItemResponse from(
            ChatMessage chatMessage,
            String senderName,
            String replySenderName
    ) {
        ChatMessage replyMessage = chatMessage.getReplyMessage();
        return new ChatMessageItemResponse(
                senderName,
                chatMessage.getId(),
                chatMessage.getText(),
                replySenderName,
                replyMessage == null ? null : replyMessage.getId(),
                chatMessage.getCreatedAt()
        );
    }
}
