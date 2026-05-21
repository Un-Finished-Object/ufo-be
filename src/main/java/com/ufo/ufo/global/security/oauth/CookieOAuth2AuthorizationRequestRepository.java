package com.ufo.ufo.global.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ufo.ufo.global.exception.OAuthAuthorizationRequestCookieException;
import io.jsonwebtoken.io.Decoders;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String AUTHORIZATION_REQUEST_COOKIE = "oauth2_authorization_request";
    private static final int COOKIE_MAX_AGE_SECONDS = 180;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final OAuthRedirectProperties oAuthRedirectProperties;
    private final ObjectMapper objectMapper;
    private final SecretKeySpec cookieSigningKey;

    public CookieOAuth2AuthorizationRequestRepository(
            OAuthRedirectProperties oAuthRedirectProperties,
            ObjectMapper objectMapper,
            @Value("${spring.jwt.secret}") String jwtSecret
    ) {
        this.oAuthRedirectProperties = oAuthRedirectProperties;
        this.objectMapper = objectMapper;
        try {
            this.cookieSigningKey = new SecretKeySpec(Decoders.BASE64.decode(jwtSecret), HMAC_ALGORITHM);
        } catch (RuntimeException e) {
            throw new OAuthAuthorizationRequestCookieException();
        }
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return findCookie(request)
                .map(Cookie::getValue)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            expireAuthorizationRequestCookie(request, response);
            return;
        }

        response.addHeader(HttpHeaders.SET_COOKIE,
                createAuthorizationRequestCookie(serialize(authorizationRequest), request.isSecure()).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        expireAuthorizationRequestCookie(request, response);
        return authorizationRequest;
    }

    private Optional<Cookie> findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (AUTHORIZATION_REQUEST_COOKIE.equals(cookie.getName())) {
                return Optional.of(cookie);
            }
        }

        return Optional.empty();
    }

    private ResponseCookie createAuthorizationRequestCookie(String value, boolean secure) {
        return createCookie(value, COOKIE_MAX_AGE_SECONDS, secure);
    }

    private void expireAuthorizationRequestCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, createCookie("", 0, request.isSecure()).toString());
    }

    private ResponseCookie createCookie(String value, int maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(AUTHORIZATION_REQUEST_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .domain(oAuthRedirectProperties.requiredCookieDomain())
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        String payload = Base64.getUrlEncoder()
                .encodeToString(writeJson(OAuth2AuthorizationRequestCookie.from(authorizationRequest))
                        .getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        String[] parts = value.split("\\.", 2);
        if (parts.length != 2 || !verify(parts[0], parts[1])) {
            return null;
        }

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(parts[0]);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, OAuth2AuthorizationRequestCookie.class)
                    .toAuthorizationRequest();
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return null;
        }
    }

    private String writeJson(OAuth2AuthorizationRequestCookie authorizationRequestCookie) {
        try {
            return objectMapper.writeValueAsString(authorizationRequestCookie);
        } catch (JsonProcessingException e) {
            throw new OAuthAuthorizationRequestCookieException();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(cookieSigningKey);
            return Base64.getUrlEncoder()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new OAuthAuthorizationRequestCookieException();
        }
    }

    private boolean verify(String payload, String signature) {
        return MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

}
