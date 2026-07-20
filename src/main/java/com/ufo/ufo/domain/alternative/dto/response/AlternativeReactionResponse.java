package com.ufo.ufo.domain.alternative.dto.response;

import java.time.LocalDateTime;

public record AlternativeReactionResponse(
        Long altSetId,
        int type,
        long likesCount,
        LocalDateTime updatedAt
) {
    public static AlternativeReactionResponse from(
            Long altSetId,
            int type,
            long likesCount,
            LocalDateTime updatedAt
    ) {
        return new AlternativeReactionResponse(altSetId, type, likesCount, updatedAt);
    }
}
