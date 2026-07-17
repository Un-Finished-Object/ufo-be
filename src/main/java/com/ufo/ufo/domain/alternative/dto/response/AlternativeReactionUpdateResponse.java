package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeReactionType;
import java.time.LocalDateTime;

public record AlternativeReactionUpdateResponse(
        Long altSetId,
        int type,
        long likesCount,
        LocalDateTime updatedAt
) {
    public static AlternativeReactionUpdateResponse from(
            Long altSetId,
            AlternativeReactionType type,
            long likesCount,
            LocalDateTime updatedAt
    ) {
        return new AlternativeReactionUpdateResponse(altSetId, type.code(), likesCount, updatedAt);
    }
}
