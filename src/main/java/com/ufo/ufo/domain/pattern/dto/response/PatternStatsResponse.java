package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.Pattern;

public record PatternStatsResponse(
        int views,
        long scraps
) {
    public static PatternStatsResponse from(Pattern pattern) {
        return new PatternStatsResponse(pattern.getViewCount(), pattern.getScrapsCount());
    }
}
