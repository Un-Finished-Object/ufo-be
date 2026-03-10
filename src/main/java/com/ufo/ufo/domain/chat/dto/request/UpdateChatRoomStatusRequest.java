package com.ufo.ufo.domain.chat.dto.request;

public record UpdateChatRoomStatusRequest(
        Boolean favorite,
        Boolean hidden
) {
    public boolean hasUpdates() {
        return favorite != null || hidden != null;
    }
}
