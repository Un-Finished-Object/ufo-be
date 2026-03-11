package com.ufo.ufo.domain.chat.dto.response;

import java.util.List;

public record UserChatRoomListResponse(List<UserChatRoomItemResponse> chats) {
    public static UserChatRoomListResponse of(List<UserChatRoomItemResponse> chats) {
        return new UserChatRoomListResponse(chats);
    }
}
