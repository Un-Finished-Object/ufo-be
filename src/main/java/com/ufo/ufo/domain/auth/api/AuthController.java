package com.ufo.ufo.domain.auth.api;

import com.ufo.ufo.domain.auth.application.AuthService;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthCookieManager oauthCookieManager;

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@CookieValue("refresh_token") String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.reissue(refreshToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, oauthCookieManager.expireRefreshTokenCookie(request.isSecure()).toString())
                .body(ApiResponse.success(Map.of()));
    }
}
