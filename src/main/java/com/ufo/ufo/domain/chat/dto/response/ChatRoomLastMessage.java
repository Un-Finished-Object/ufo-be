package com.ufo.ufo.domain.chat.dto.response;

public record ChatRoomLastMessage(
        Long chatId,
        String lastMessage
) {
}
