package com.ufo.ufo.domain.chat.dto.websocket.request;

public record ChatMessageSendRequest(
        Long roomId,
        String text,
        String clientMessageId,
        Boolean isReply,
        Long replyMessageId
) {
    public boolean replyRequested() {
        return Boolean.TRUE.equals(isReply);
    }
}
