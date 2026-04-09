package com.ufo.ufo.domain.pattern.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlternativeGaugeRequest(
        @NotBlank(message = "needleSize 필드의 정보가 올바르지 않습니다.")
        String needleSize,
        @NotNull(message = "stitch 필드의 정보가 올바르지 않습니다.")
        Integer stitch,
        @NotNull(message = "row 필드의 정보가 올바르지 않습니다.")
        Integer row
) {
}
