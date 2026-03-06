package com.ufo.ufo.domain.scrap.dto.response;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import java.util.List;

public record MyScrapsResponse(
        List<Item> scraps
) {
    public static MyScrapsResponse from(List<Item> scraps) {
        return new MyScrapsResponse(scraps);
    }

    public record Item(
            Long id,
            String title,
            String thumbnailUrl,
            String author
    ) {
        public static Item from(Pattern pattern) {
            return new Item(
                    pattern.getId(),
                    pattern.getTitle(),
                    pattern.getThumbnailUrl(),
                    pattern.getDesigner()
            );
        }
    }
}
