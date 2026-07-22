package com.ufo.ufo.domain.pattern.dto.response;

import com.ufo.ufo.domain.pattern.domain.Yarn;

public record YarnResponse(
        Long yarnId,
        String yarnName,
        Integer ply,
        Integer weight,
        Integer cost,
        String component,
        String store,
        Integer length
) {
    public static YarnResponse from(Yarn yarn) {
        return new YarnResponse(
                yarn.getYarnId(),
                yarn.getName() == null ? "" : yarn.getName(),
                yarn.getPly(),
                yarn.getWeightG() == null ? 0 : yarn.getWeightG(),
                yarn.getPrice() == null ? 0 : yarn.getPrice(),
                yarn.getSubComponent() == null ? "" : yarn.getSubComponent(),
                yarn.getVendor() == null ? "" : yarn.getVendor(),
                yarn.getLength() == null ? 0 : yarn.getLength()
        );
    }
}
