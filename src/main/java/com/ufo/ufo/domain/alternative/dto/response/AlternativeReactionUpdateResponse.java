package com.ufo.ufo.domain.alternative.dto.response;

import com.ufo.ufo.domain.alternative.domain.AlternativeReactionType;
import java.time.LocalDateTime;

public record AlternativeReactionUpdateResponse(
        Long altId,
        int type,
        long likesCount,
        long dislikesCount,
        LocalDateTime updatedAt
) {
    public static AlternativeReactionUpdateResponse from(
            Long altId,
            AlternativeReactionType type,
            long likesCount,
            long dislikesCount,
            LocalDateTime updatedAt
    ) {
        return new AlternativeReactionUpdateResponse(altId, type.code(), likesCount, dislikesCount, updatedAt);
    }
}
