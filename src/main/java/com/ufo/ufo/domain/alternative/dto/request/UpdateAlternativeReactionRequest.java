package com.ufo.ufo.domain.alternative.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateAlternativeReactionRequest(
        @NotNull(message = "type 필드의 정보가 올바르지 않습니다.")
        @Min(value = 1, message = "type은 1(추천), 2(비추천), 3(취소) 값만 가능합니다.")
        @Max(value = 3, message = "type은 1(추천), 2(비추천), 3(취소) 값만 가능합니다.")
        Integer type
) {
}
