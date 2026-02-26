package com.ufo.ufo.domain.pattern.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateAlternativeYarnRequest(
        @JsonProperty("yarn_name")
        @NotBlank(message = "yarnName 필드의 정보가 올바르지 않습니다.")
        String yarnName,
        @JsonProperty("yarn_uri")
        @NotBlank(message = "yarnUri 필드의 정보가 올바르지 않습니다.")
        String yarnUri,
        @NotNull(message = "weight 필드의 정보가 올바르지 않습니다.")
        Integer weight,
        @NotNull(message = "cost 필드의 정보가 올바르지 않습니다.")
        Integer cost,
        @NotBlank(message = "gauge 필드의 정보가 올바르지 않습니다.")
        String gauge,
        @NotBlank(message = "store 필드의 정보가 올바르지 않습니다.")
        String store
) {
}
