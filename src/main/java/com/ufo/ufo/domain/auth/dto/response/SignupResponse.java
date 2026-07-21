package com.ufo.ufo.domain.auth.dto.response;

import java.util.List;

public record SignupResponse(
        Long userId,
        String userName,
        String profileImageUrl,
        List<String> keywords
) {
}
