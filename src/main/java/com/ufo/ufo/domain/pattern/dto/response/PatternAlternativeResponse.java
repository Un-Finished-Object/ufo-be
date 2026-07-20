package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnGauge;
import java.util.List;

public record PatternAlternativeResponse(
        Long altId,
        Long yarnId,
        String yarnName,
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

    public static PatternAlternativeResponse from(PatternAlternativeYarn alternative) {
        Yarn yarn = alternative.getYarn();
        return new PatternAlternativeResponse(
                alternative.getId(),
                yarn.getYarnId(),
                yarn.getName(),
                yarn.getWeightG() == null ? 0 : yarn.getWeightG(),
                yarn.getPrice() == null ? 0 : yarn.getPrice(),
                yarn.getMainComponent() == null ? "" : yarn.getMainComponent(),
                yarn.getSubComponent() == null ? "" : yarn.getSubComponent(),
                yarn.getVendor() == null ? "" : yarn.getVendor(),
                yarn.getThickness() == null ? "" : yarn.getThickness(),
                yarn.getLength() == null ? 0 : yarn.getLength(),
                yarn.getGauges().stream().map(Gauge::from).toList(),
                alternative.getUser() == null ? "" : alternative.getUser().getNickname()
        );
    }
}
