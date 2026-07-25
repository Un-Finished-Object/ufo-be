package com.ufo.ufo.domain.chat.dto.response;

import java.time.LocalDateTime;

public record AdminUnreadChatRoomItemResponse(
        Long chatId,
        Long patternId,
        String chatName,
        String chatImageUrl,
        int unRead,
        String lastMessage,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {
}
