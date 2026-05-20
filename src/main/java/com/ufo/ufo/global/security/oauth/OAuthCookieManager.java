package com.ufo.ufo.global.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthCookieManager {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final OAuthRedirectProperties oAuthRedirectProperties;

    public ResponseCookie createRefreshTokenCookie(String refreshToken, long maxAgeSeconds, boolean secure) {
        String cookieDomain = oAuthRedirectProperties.requiredCookieDomain();
        String sameSite = secure ? "None" : "Lax";

        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .domain(cookieDomain)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie expireRefreshTokenCookie(boolean secure) {
        String cookieDomain = oAuthRedirectProperties.requiredCookieDomain();
        String sameSite = secure ? "None" : "Lax";

        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .domain(cookieDomain)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }

}
