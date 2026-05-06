package com.ufo.ufo.domain.pattern.dto.response;

public record PatternPurchaseStatusResponse(
        Long userId,
        boolean chat,
        boolean alternative,
        Long chatRoomId
) {
    public static PatternPurchaseStatusResponse from(Long userId, boolean chat, boolean alternative, Long chatRoomId) {
        return new PatternPurchaseStatusResponse(userId, chat, alternative, chatRoomId);
    }
}
