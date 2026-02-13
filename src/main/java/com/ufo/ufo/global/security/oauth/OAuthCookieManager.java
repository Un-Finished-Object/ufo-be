package com.ufo.ufo.global.security.oauth;

import com.ufo.ufo.global.exception.RedirectUriNotFoundException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
public class OAuthCookieManager {

    public static final String REDIRECT_URI_COOKIE = "oauth_redirect_uri";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    public ResponseCookie createRedirectUriCookie(String redirectUri, boolean secure) {
        return ResponseCookie.from(REDIRECT_URI_COOKIE, URLEncoder.encode(redirectUri, StandardCharsets.UTF_8))
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie expireRedirectUriCookie(boolean secure) {
        return ResponseCookie.from(REDIRECT_URI_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("None")
                .build();
    }

    public ResponseCookie expireRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();
    }

    public String extractRedirectUri(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, REDIRECT_URI_COOKIE);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            throw new RedirectUriNotFoundException();
        }
        return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
    }
}
