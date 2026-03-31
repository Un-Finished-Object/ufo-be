package com.ufo.ufo.domain.image.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImagePresignedUrlIssueRequest(
        @NotNull(message = "fileCount는 필수입니다.")
        @Min(value = 1, message = "fileCount는 1 이상이어야 합니다.")
        @Max(value = 5, message = "fileCount는 5 이하여야 합니다.")
        Integer fileCount,

        @NotBlank(message = "purpose는 필수입니다.")
        String purpose
) {
}
