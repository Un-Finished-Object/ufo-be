package com.ufo.ufo.domain.chat.dto.websocket.response;

import java.time.LocalDateTime;

public record ChatReadUpdatedPayload(
        Long userId,
        Long lastReadMessageId,
        LocalDateTime readAt
) {
}
