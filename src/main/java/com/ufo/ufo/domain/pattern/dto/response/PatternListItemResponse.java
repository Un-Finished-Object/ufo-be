package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import java.time.LocalDateTime;

public record PatternListItemResponse(
        Long id,
        String title,
        String thumbnailUrl,
        String category,
        String subCategory,
        String author,
        PatternStatsResponse stats,
        PatternMyResponse my,
        LocalDateTime createdAt
) {
    public static PatternListItemResponse from(Pattern pattern, boolean scrapped, String thumbnailUrl) {
        return new PatternListItemResponse(
                pattern.getId(),
                pattern.getTitle(),
                thumbnailUrl,
                pattern.getCategoryMain(),
                pattern.getCategorySub(),
                pattern.getDesigner(),
                PatternStatsResponse.from(pattern),
                new PatternMyResponse(scrapped),
                pattern.getCreatedAt()
        );
    }
}
