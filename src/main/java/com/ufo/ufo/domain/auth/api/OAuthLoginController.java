package com.ufo.ufo.domain.auth.api;

import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import com.ufo.ufo.global.security.types.Provider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/login")
@RequiredArgsConstructor
public class OAuthLoginController {

    private static final String OAUTH2_AUTHORIZATION_BASE_URI = "/oauth2/authorization/";

    private final OAuthCookieManager oauthCookieManager;

    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(
            @PathVariable String provider,
            @RequestParam("redirect_uri") String redirectUri,
            HttpServletRequest request
    ) {
        Provider providerType = Provider.from(provider);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, oauthCookieManager.createRedirectUriCookie(redirectUri, request.isSecure()).toString())
                .header(HttpHeaders.LOCATION, OAUTH2_AUTHORIZATION_BASE_URI + providerType.getRegistrationId())
                .build();
    }
}
