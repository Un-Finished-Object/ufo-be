package com.ufo.ufo.domain.alternative.dto.response;

public record AlternativeReactionResponse(
        Long altId,
        long likeCount,
        long dislikeCount
) {
    public static AlternativeReactionResponse from(Long altId, long likeCount, long dislikeCount) {
        return new AlternativeReactionResponse(altId, likeCount, dislikeCount);
    }
}
