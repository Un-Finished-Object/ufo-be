package com.ufo.ufo.domain.chat.dto.websocket.response;

import java.time.LocalDateTime;

public record ChatMessageCreatedPayload(
        Long messageId,
        String clientMessageId,
        Long senderId,
        String senderProfile,
        String senderName,
        String text,
        String replySenderName,
        Long replyMessageId,
        LocalDateTime createdAt
) {
}
