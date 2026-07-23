package com.ufo.ufo.domain.chat.dto.response;

import java.time.LocalDateTime;

public record UserChatRoomItemResponse(
        Long patternId,
        Long chatId,
        String nickname,
        String chatName,
        String chatImageUrl,
        boolean favorite,
        boolean isHidden,
        int unRead,
        int userCount,
        String lastMessage,
        LocalDateTime createdAt
) {
    public static UserChatRoomItemResponse of(Long patternId, Long chatId, String nickname, String chatName, String chatImageUrl,
                                              boolean favorite, boolean isHidden, int unRead, int userCount,
                                              String lastMessage,
                                              LocalDateTime createdAt) {
        return new UserChatRoomItemResponse(
                patternId,
                chatId,
                nickname,
                chatName,
                chatImageUrl,
                favorite,
                isHidden,
                unRead,
                userCount,
                lastMessage,
                createdAt
        );
    }
}
