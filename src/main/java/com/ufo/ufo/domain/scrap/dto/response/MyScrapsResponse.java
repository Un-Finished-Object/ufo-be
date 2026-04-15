package com.ufo.ufo.domain.scrap.dto.response;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.response.PatternMyResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternStatsResponse;
import java.time.LocalDateTime;
import java.util.List;

public record MyScrapsResponse(
        List<Item> items,
        int page,
        int nextPage
) {
    public static MyScrapsResponse from(List<Item> items, int page, int nextPage) {
        return new MyScrapsResponse(items, page, nextPage);
    }

    public record Item(
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
        public static Item from(Pattern pattern) {
            return new Item(
                    pattern.getId(),
                    pattern.getTitle(),
                    pattern.getThumbnailUrl(),
                    pattern.getCategoryMain(),
                    pattern.getCategorySub(),
                    pattern.getDesigner(),
                    PatternStatsResponse.from(pattern),
                    new PatternMyResponse(true),
                    pattern.getCreatedAt()
            );
        }
    }
}
