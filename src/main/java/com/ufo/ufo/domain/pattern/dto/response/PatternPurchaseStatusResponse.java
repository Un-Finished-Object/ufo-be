package com.ufo.ufo.domain.pattern.dto.response;

public record PatternPurchaseStatusResponse(
        Long userId,
        boolean chat,
        boolean alternative
) {
    public static PatternPurchaseStatusResponse from(Long userId, boolean chat, boolean alternative) {
        return new PatternPurchaseStatusResponse(userId, chat, alternative);
    }
}
