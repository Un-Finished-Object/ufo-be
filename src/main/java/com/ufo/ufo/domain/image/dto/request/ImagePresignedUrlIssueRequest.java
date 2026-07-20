package com.ufo.ufo.domain.image.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImagePresignedUrlIssueRequest(
        @NotNull(message = "fileCount는 필수입니다.")
        @Min(value = 1, message = "fileCount는 1 이상이어야 합니다.")
        Integer fileCount,

        @NotBlank(message = "purpose는 필수입니다.")
        String purpose,

        @Min(value = 1, message = "targetId는 1 이상이어야 합니다.")
        Long targetId,

        @NotNull(message = "files는 필수입니다.")
        @Size(min = 1, message = "files는 1개 이상이어야 합니다.")
        List<@Valid FileInfo> files
) {
    public record FileInfo(
            @NotBlank(message = "contentType은 필수입니다.")
            String contentType,

            @NotNull(message = "contentLength는 필수입니다.")
            @Min(value = 1, message = "contentLength는 1 이상이어야 합니다.")
            Long contentLength
    ) {
    }
}
