package com.ufo.ufo.domain.chat.dto.response;

public record ChatRoomStatusResponse(
        Long chatId,
        boolean favorite,
        boolean isHidden
) {
    public static ChatRoomStatusResponse of(Long chatId, boolean favorite, boolean isHidden) {
        return new ChatRoomStatusResponse(chatId, favorite, isHidden);
    }
}
