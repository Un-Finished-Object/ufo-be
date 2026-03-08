package com.ufo.ufo.domain.chat.dto.websocket.response;

public record ChatErrorPayload(
        String code,
        String message,
        String clientMessageId
) {
}
