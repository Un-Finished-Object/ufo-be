package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnGauge;
import java.util.List;

public record PatternAlternativesResponse(List<Item> items) {
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
            public static Gauge from(YarnGauge gauge) {
                return new Gauge(
                        gauge.getNeedleSize(),
                        gauge.getStitch(),
                        gauge.getRowCount()
                );
            }
        }

        public static Item from(PatternAlternativeYarn alternative) {
            Yarn yarn = alternative.getYarn();
            return from(yarn, alternative.getId(), alternative.getImageUrl(),
                    alternative.getUser() == null ? "" : alternative.getUser().getNickname());
        }

        private static Item from(Yarn yarn, Long altId, String yarnUri, String username) {
            return new Item(
                    altId,
                    yarn.getYarnId(),
                    yarn.getName(),
                    yarnUri == null ? "" : yarnUri,
                    yarn.getWeightG() == null ? 0 : yarn.getWeightG(),
                    yarn.getPrice() == null ? 0 : yarn.getPrice(),
                    yarn.getMainComponent() == null ? "" : yarn.getMainComponent(),
                    yarn.getSubComponent() == null ? "" : yarn.getSubComponent(),
                    yarn.getVendor() == null ? "" : yarn.getVendor(),
                    yarn.getThickness() == null ? "" : yarn.getThickness(),
                    yarn.getLength() == null ? 0 : yarn.getLength(),
                    yarn.getGauges().stream().map(Gauge::from).toList(),
                    username
            );
        }
    }
}
