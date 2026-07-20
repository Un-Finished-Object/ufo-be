package com.ufo.ufo.domain.pattern.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateAlternativeRequest(
        @NotBlank(message = "yarnName 필드의 정보가 올바르지 않습니다.")
        String yarnName,
        @NotNull(message = "weight 필드의 정보가 올바르지 않습니다.")
        Integer weight,
        @NotNull(message = "cost 필드의 정보가 올바르지 않습니다.")
        Integer cost,
        @NotBlank(message = "mainComponent 필드의 정보가 올바르지 않습니다.")
        String mainComponent,
        @NotBlank(message = "subComponent 필드의 정보가 올바르지 않습니다.")
        String subComponent,
        @NotBlank(message = "store 필드의 정보가 올바르지 않습니다.")
        String store,
        @NotBlank(message = "thickness 필드의 정보가 올바르지 않습니다.")
        String thickness,
        @NotNull(message = "length 필드의 정보가 올바르지 않습니다.")
        Integer length,
        @NotEmpty(message = "gauges 필드의 정보가 올바르지 않습니다.")
        @Valid List<@NotNull(message = "gauges 항목의 정보가 올바르지 않습니다.") AlternativeGaugeRequest> gauges
) {
}
