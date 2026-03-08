package com.ufo.ufo.domain.chat.dto.websocket.response;

import java.time.LocalDateTime;

public record ChatMessageCreatedPayload(
        Long messageId,
        String clientMessageId,
        Long senderId,
        String senderName,
        String text,
        LocalDateTime createdAt
) {
}
