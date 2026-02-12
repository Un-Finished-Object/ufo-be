package com.ufo.ufo.domain.auth.dto.response;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
