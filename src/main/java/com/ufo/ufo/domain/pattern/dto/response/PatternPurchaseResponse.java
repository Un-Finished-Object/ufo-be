package com.ufo.ufo.domain.pattern.dto.response;

public record PatternPurchaseResponse(
        Long userId,
        String type
) {
    public static PatternPurchaseResponse from(Long userId, String type) {
        return new PatternPurchaseResponse(userId, type);
    }
}
