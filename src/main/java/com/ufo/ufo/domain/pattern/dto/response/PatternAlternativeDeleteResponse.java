package com.ufo.ufo.domain.pattern.dto.response;

public record PatternAlternativeDeleteResponse(
        Long userId,
        Long altId
) {
    public static PatternAlternativeDeleteResponse from(Long userId, Long altId) {
        return new PatternAlternativeDeleteResponse(userId, altId);
    }
}
