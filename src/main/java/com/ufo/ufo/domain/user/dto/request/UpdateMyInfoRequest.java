package com.ufo.ufo.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMyInfoRequest(
        @Pattern(regexp = ".*\\S.*", message = "userName 필드의 정보가 올바르지 않습니다.")
        @Size(min = 2, max = 20, message = "userName은 2자 이상 20자 이하여야 합니다.")
        String userName,

        @Pattern(regexp = ".*\\S.*", message = "profileImage 필드의 정보가 올바르지 않습니다.")
        @Size(max = 2048, message = "profileImage는 2048자 이하여야 합니다.")
        String profileImage
) {
}
