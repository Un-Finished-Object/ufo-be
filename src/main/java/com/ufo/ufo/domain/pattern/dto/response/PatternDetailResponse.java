package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import java.util.List;

public record PatternDetailResponse(
        Long id,
        String title,
        List<String> images,
        String author,
        Meta meta,
        PatternStatsResponse stats,
        PatternMyResponse my
) {
    public static PatternDetailResponse from(Pattern pattern, List<String> images, boolean isScrapped) {
        return new PatternDetailResponse(
                pattern.getId(),
                pattern.getTitle(),
                images,
                pattern.getDesigner(),
                Meta.from(pattern),
                PatternStatsResponse.from(pattern),
                new PatternMyResponse(isScrapped)
        );
    }

    public record Meta(
            String category,
            String subCategory,
            String gauge,
            String originalYarn,
            String originalNeedle,
            String requiredYarnAmount,
            String size,
            String actualSize
    ) {
        public static Meta from(Pattern pattern) {
            return new Meta(
                    pattern.getCategoryMain(),
                    pattern.getCategorySub(),
                    pattern.getGauge(),
                    pattern.getOriginalYarn(),
                    pattern.getNeedleSize(),
                    pattern.getRequiredYarnAmount(),
                    pattern.getSize(),
                    pattern.getActualSize()
            );
        }
    }

}
