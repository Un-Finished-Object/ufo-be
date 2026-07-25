package com.ufo.ufo.domain.chat.dto.response;

import java.time.LocalDateTime;

public record AdminUnreadChatRoomInfo(
        Long chatId,
        Long patternId,
        String chatName,
        String thumbnailUrl,
        Long unRead,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {
}
