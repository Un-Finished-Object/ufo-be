package com.ufo.ufo.domain.auth.api;

import com.ufo.ufo.domain.auth.application.AuthService;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestHeader("Cookie") String refreshToken) {
        String token = refreshToken.replace("Bearer ", "");
        return ResponseEntity.ok(authService.reissue(token));
    }
}
