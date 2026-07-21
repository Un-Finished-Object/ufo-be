package com.ufo.ufo.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignupRequest(
        @NotBlank(message = "userName 필드는 필수입니다.")
        @Pattern(regexp = ".*\\S.*", message = "userName 필드의 정보가 올바르지 않습니다.")
        @Size(min = 2, max = 20, message = "userName은 2자 이상 20자 이하여야 합니다.")
        String userName,

        @NotBlank(message = "profileImageKey 필드는 필수입니다.")
        @Size(max = 1024, message = "profileImageKey는 1024자 이하여야 합니다.")
        String profileImageKey,

        @NotNull(message = "keywords 필드는 필수입니다.")
        @Size(max = 3, message = "keywords는 최대 3개까지 입력할 수 있습니다.")
        List<@NotBlank @Size(max = 50) String> keywords
) {
}
