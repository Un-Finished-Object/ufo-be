package com.ufo.ufo.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyInfoRequest(
        @Size(min = 2, max = 20, message = "userName은 2자 이상 20자 이하여야 합니다.")
        String userName,

        @Size(max = 2048, message = "profileImage는 2048자 이하여야 합니다.")
        String profileImage
) {
}
