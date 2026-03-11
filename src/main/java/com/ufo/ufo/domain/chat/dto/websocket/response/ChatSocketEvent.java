package com.ufo.ufo.domain.chat.dto.websocket.response;

public record ChatSocketEvent<T>(
        ChatEventType eventType,
        Long roomId,
        T payload
) {
}
