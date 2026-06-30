package com.ufo.ufo.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyInfoRequest(
        @NotBlank(message = "nickname 필드의 정보가 올바르지 않습니다.")
        @Size(min = 2, max = 20, message = "nickname은 2자 이상 20자 이하여야 합니다.")
        String nickname,

        @NotBlank(message = "profileImage 필드의 정보가 올바르지 않습니다.")
        @Size(max = 2048, message = "profileImage는 2048자 이하여야 합니다.")
        String profileImage
) {
}
