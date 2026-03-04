package com.ufo.ufo.domain.alternative.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateAlternativeCommentRequest(
        @NotBlank(message = "content 필드의 정보가 올바르지 않습니다.")
        String content
) {
}
