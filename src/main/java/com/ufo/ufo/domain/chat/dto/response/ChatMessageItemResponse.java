package com.ufo.ufo.domain.chat.dto.response;

import com.ufo.ufo.domain.chat.domain.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageItemResponse(
        Long senderId,
        String senderName,
        Long messageId,
        String text,
        String replySenderName,
        Long replyMessageId,
        LocalDateTime createdAt
) {
    public static ChatMessageItemResponse from(ChatMessage chatMessage) {
        ChatMessage replyMessage = chatMessage.getReplyMessage();
        return new ChatMessageItemResponse(
                chatMessage.getUser().getId(),
                chatMessage.getUser().getNickname(),
                chatMessage.getId(),
                chatMessage.getText(),
                replyMessage == null ? null : replyMessage.getUser().getNickname(),
                replyMessage == null ? null : replyMessage.getId(),
                chatMessage.getCreatedAt()
        );
    }
}
