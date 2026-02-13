package com.ufo.ufo.domain.auth.api;

import com.ufo.ufo.domain.auth.application.AuthService;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@CookieValue("refresh_token") String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.reissue(refreshToken)));
    }
}
