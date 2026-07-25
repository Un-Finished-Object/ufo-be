package com.ufo.ufo.domain.chat.dto.response;

import java.util.List;

public record AdminUnreadChatRoomListResponse(
        List<AdminUnreadChatRoomItemResponse> chats,
        int page,
        int nextPages
) {
    public static AdminUnreadChatRoomListResponse of(
            List<AdminUnreadChatRoomItemResponse> chats,
            int page,
            int nextPages
    ) {
        return new AdminUnreadChatRoomListResponse(chats, page, nextPages);
    }
}
