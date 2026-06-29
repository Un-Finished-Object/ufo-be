package com.ufo.ufo.domain.chat.dto.response;

import java.util.List;

public record UserChatRoomListResponse(
        List<UserChatRoomItemResponse> chats,
        int page,
        int nextPage
) {
    public static UserChatRoomListResponse of(List<UserChatRoomItemResponse> chats, int page, int nextPage) {
        return new UserChatRoomListResponse(chats, page, nextPage);
    }
}
