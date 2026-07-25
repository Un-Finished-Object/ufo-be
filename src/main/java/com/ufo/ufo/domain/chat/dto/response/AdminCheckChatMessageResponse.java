package com.ufo.ufo.domain.chat.dto.response;

import java.time.LocalDateTime;

public record AdminCheckChatMessageResponse(
        Long chatRoomId,
        Long messageId,
        LocalDateTime checkedAt
) {
    public static AdminCheckChatMessageResponse of(Long chatRoomId, Long messageId, LocalDateTime checkedAt) {
        return new AdminCheckChatMessageResponse(chatRoomId, messageId, checkedAt);
    }
}
