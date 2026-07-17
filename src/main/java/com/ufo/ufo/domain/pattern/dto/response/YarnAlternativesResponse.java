package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.PatternAlternativeYarn;
import com.ufo.ufo.domain.pattern.domain.Yarn;
import java.util.List;

public record YarnAlternativesResponse(
        Long originalYarnSetId,
        List<Item> firstYarn,
        List<Item> secondYarn,
        List<Item> subYarn
) {

    public record Item(
            Long altId,
            Integer ranking,
            Long yarnId,
            String yarnName,
            Integer ply,
            Integer weight,
            Integer cost,
            String component,
            String store,
            String thickness,
            Integer length,
            Integer componentScore,
            Integer lengthScore,
            Integer gaugeScore,
            Integer needleScore,
            String username
    ) {

        public static Item from(PatternAlternativeYarn alternative) {
            Yarn yarn = alternative.getYarn();
            return new Item(
                    alternative.getId(),
                    alternative.getRanking(),
                    yarn.getYarnId(),
                    yarn.getName(),
                    null,
                    yarn.getWeightG(),
                    yarn.getPrice(),
                    yarn.getSubComponent(),
                    yarn.getVendor(),
                    yarn.getThickness(),
                    yarn.getLength(),
                    alternative.getComponentScore(),
                    alternative.getLengthScore(),
                    alternative.getGaugeScore(),
                    alternative.getNeedleScore(),
                    alternative.getUser().getNickname()
            );
        }
    }
}
