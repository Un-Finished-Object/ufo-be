package com.ufo.ufo.domain.chat.dto.response;

public record UserChatRoomItemResponse(
        Long chatId,
        String chatName,
        String chatImageUrl,
        boolean favorite,
        boolean isHidden,
        int unRead
) {
    public static UserChatRoomItemResponse of(Long chatId, String chatName, String chatImageUrl,
                                              boolean favorite, boolean isHidden, int unRead) {
        return new UserChatRoomItemResponse(chatId, chatName, chatImageUrl, favorite, isHidden, unRead);
    }
}
