package com.ufo.ufo.domain.chat.dto.response;

import java.time.LocalDateTime;

public record AdminDeleteChatMessageResponse(
        Long chatRoomId,
        Long messageId,
        LocalDateTime deletedAt
) {
    public static AdminDeleteChatMessageResponse of(Long chatRoomId, Long messageId, LocalDateTime deletedAt) {
        return new AdminDeleteChatMessageResponse(chatRoomId, messageId, deletedAt);
    }
}
