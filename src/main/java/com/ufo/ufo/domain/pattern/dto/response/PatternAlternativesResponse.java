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
            Long yarnId,
            String yarnName,
            String yarnUri,
            Integer weight,
            Integer cost,
            String mainComponent,
            String subComponent,
            String store,
            String thickness,
            Integer length,
            List<Gauge> gauges,
            String username
    ) {
        public record Gauge(
                String needleSize,
                Integer stitch,
                Integer row
        ) {
            public static Gauge from(com.ufo.ufo.domain.pattern.domain.AlternativeYarnGauge gauge) {
                return new Gauge(
                        gauge.getNeedleSize(),
                        gauge.getStitch(),
                        gauge.getRowCount()
                );
            }
        }

        public static Item from(PatternAlternativeYarn alternative) {
            return new Item(
                    alternative.getId(),
                    alternative.getYarn().getYarnId(),
                    alternative.getYarn().getName(),
                    alternative.getImageUrl(),
                    alternative.getYarn().getWeightG() == null ? 0 : alternative.getYarn().getWeightG(),
                    alternative.getYarn().getPrice() == null ? 0 : alternative.getYarn().getPrice(),
                    alternative.getYarn().getMainComponent() == null ? "" : alternative.getYarn().getMainComponent(),
                    alternative.getYarn().getSubComponent() == null ? "" : alternative.getYarn().getSubComponent(),
                    alternative.getYarn().getVendor() == null ? "" : alternative.getYarn().getVendor(),
                    alternative.getYarn().getThickness() == null ? "" : alternative.getYarn().getThickness(),
                    alternative.getYarn().getLength() == null ? 0 : alternative.getYarn().getLength(),
                    alternative.getGauges().stream().map(Gauge::from).toList(),
                    alternative.getUser() == null ? "" : alternative.getUser().getNickname()
            );
        }
    }
}
