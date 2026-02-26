package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import java.util.List;

public record PatternAlternativesResponse(List<Item> items) {
    public static PatternAlternativesResponse from(List<Item> items) {
        return new PatternAlternativesResponse(items);
    }

    public static PatternAlternativesResponse fromAlternatives(List<PatternAlternativeYarn> alternatives) {
        return new PatternAlternativesResponse(alternatives.stream().map(Item::from).toList());
    }

    public record Item(
            Long altId,
            String yarnName,
            String yarnUri,
            Integer cost,
            Integer weight,
            String gauge,
            String store,
            String username
    ) {
        public static Item from(PatternAlternativeYarn alternative) {
            return new Item(
                    alternative.getId(),
                    alternative.getYarn().getName(),
                    alternative.getImageUrl(),
                    alternative.getYarn().getPrice() == null ? 0 : alternative.getYarn().getPrice(),
                    alternative.getYarn().getWeightG() == null ? 0 : alternative.getYarn().getWeightG(),
                    alternative.getGauge() == null ? "" : alternative.getGauge(),
                    alternative.getYarn().getVendor() == null ? "" : alternative.getYarn().getVendor(),
                    alternative.getUser() == null ? "" : alternative.getUser().getNickname()
            );
        }
    }
}
