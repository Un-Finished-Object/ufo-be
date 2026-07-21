package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;

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
        String username
) {
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
                alternative.getUser() == null ? "" : alternative.getUser().getNickname()
        );
    }
}
