package com.ufo.ufo.domain.alternative.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAlternativeCommentRequest(
        @NotBlank(message = "content 필드의 정보가 올바르지 않습니다.")
        @Size(max = 1000, message = "content는 1000자 이하여야 합니다.")
        String content
) {
}
