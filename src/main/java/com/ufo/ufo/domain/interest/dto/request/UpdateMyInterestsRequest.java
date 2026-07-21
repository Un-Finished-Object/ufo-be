package com.ufo.ufo.domain.interest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateMyInterestsRequest(
        @NotNull(message = "keywords 필드는 필수입니다.")
        @Size(max = 3, message = "keywords는 최대 3개까지 입력할 수 있습니다.")
        List<@Size(min = 1, max = 50) String> keywords
) {
}
