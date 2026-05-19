package com.ufo.ufo.global.security.oauth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class OAuthCookieManager {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    public ResponseCookie createRefreshTokenCookie(String refreshToken, long maxAgeSeconds, boolean secure) {
        String sameSite = secure ? "None" : "Lax";
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie expireRefreshTokenCookie(boolean secure) {
        String sameSite = secure ? "None" : "Lax";
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }

}
