package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.Yarn;
import com.ufo.ufo.domain.pattern.domain.YarnAlternative;
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
            Integer needleScore
    ) {

        public static Item from(YarnAlternative alternative) {
            Yarn yarn = alternative.getAlternativeYarn();
            return new Item(
                    alternative.getId(),
                    alternative.getRanking(),
                    yarn.getYarnId(),
                    yarn.getName(),
                    yarn.getPly(),
                    yarn.getWeightG(),
                    yarn.getPrice(),
                    yarn.getSubComponent(),
                    yarn.getVendor(),
                    yarn.getThickness(),
                    yarn.getLength(),
                    alternative.getComponentScore(),
                    alternative.getLengthScore(),
                    alternative.getGaugeScore(),
                    alternative.getNeedleScore()
            );
        }
    }
}
