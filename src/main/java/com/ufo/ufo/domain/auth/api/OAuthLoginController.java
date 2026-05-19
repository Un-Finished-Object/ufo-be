package com.ufo.ufo.domain.auth.api;

import com.ufo.ufo.global.security.types.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/login")
@RequiredArgsConstructor
public class OAuthLoginController {

    private static final String OAUTH2_AUTHORIZATION_BASE_URI = "/oauth2/authorization/";

    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(
            @PathVariable("provider") String provider
    ) {
        Provider providerType = Provider.from(provider);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, OAUTH2_AUTHORIZATION_BASE_URI + providerType.getRegistrationId())
                .build();
    }
}
