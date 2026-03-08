package com.ufo.ufo.domain.chat.dto.websocket.request;

public record ChatReadUpdateRequest(
        Long roomId,
        Long lastReadMessageId
) {
}
