package com.ufo.ufo.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JWT 토큰 용도 검증 테스트")
class JwtTokenProviderTest {

    private static final byte[] KEY_BYTES = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET = Base64.getEncoder().encodeToString(KEY_BYTES);

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET);
        ReflectionTestUtils.setField(provider, "accessTokenExpireTime", 60_000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpireTime", 120_000L);
    }

    @Test
    @DisplayName("새 Access Token과 Refresh Token에는 각각 용도가 기록되어야 한다")
    void createdTokens_ContainDifferentTypes() {
        String accessToken = provider.createAccessToken("user@example.com", "ROLE_USER");
        String refreshToken = provider.createRefreshToken("user@example.com");
        SecretKey key = Keys.hmacShaKeyFor(KEY_BYTES);

        assertThat(Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken)
                .getPayload().get("token_type", String.class)).isEqualTo("ACCESS");
        assertThat(Jwts.parser().verifyWith(key).build().parseSignedClaims(refreshToken)
                .getPayload().get("token_type", String.class)).isEqualTo("REFRESH");
    }

    @Test
    @DisplayName("Refresh Token으로 사용자 인증 정보를 만들 수 없어야 한다")
    void refreshToken_CannotCreateAuthentication() {
        String refreshToken = provider.createRefreshToken("user@example.com");

        assertThatThrownBy(() -> provider.getAuthentication(refreshToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_GUEST", "ROLE_USER"})
    @DisplayName("Access Token은 게스트와 회원 모두 인증하고 재발급에는 사용할 수 없어야 한다")
    void accessToken_AuthenticatesOnlyAccessRequests(String role) {
        String accessToken = provider.createAccessToken("user@example.com", role);

        assertThat(provider.validateAccessToken(accessToken)).isTrue();
        assertThat(provider.getAuthentication(accessToken).getAuthorities())
                .extracting("authority").containsExactly(role);
        assertThat(provider.getRefreshTokenSubject(accessToken)).isEmpty();
    }

    @Test
    @DisplayName("Refresh Token은 재발급에만 사용할 수 있어야 한다")
    void refreshToken_IsAcceptedOnlyForReissue() {
        String refreshToken = provider.createRefreshToken("user@example.com");

        assertThat(provider.getRefreshTokenSubject(refreshToken)).contains("user@example.com");
        assertThat(provider.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("기존 형식의 토큰은 역할 유무로 용도를 구분해 만료 전까지 허용해야 한다")
    void legacyTokens_AreAcceptedOnlyForTheirOriginalPurpose() {
        SecretKey key = Keys.hmacShaKeyFor(KEY_BYTES);
        Date expiration = new Date(System.currentTimeMillis() + 60_000L);
        String legacyAccessToken = Jwts.builder().subject("user@example.com")
                .claim("role", "ROLE_USER").expiration(expiration).signWith(key).compact();
        String legacyRefreshToken = Jwts.builder().subject("user@example.com")
                .claim("role", null).expiration(expiration).signWith(key).compact();

        assertThat(provider.validateAccessToken(legacyAccessToken)).isTrue();
        assertThat(provider.getRefreshTokenSubject(legacyAccessToken)).isEmpty();
        assertThat(provider.validateAccessToken(legacyRefreshToken)).isFalse();
        assertThat(provider.getRefreshTokenSubject(legacyRefreshToken)).contains("user@example.com");
    }

    @Test
    @DisplayName("용도와 역할이 충돌하는 서명 토큰은 두 인증 경로에서 모두 거부해야 한다")
    void conflictingClaims_AreRejected() {
        String token = Jwts.builder().subject("user@example.com")
                .claim("role", "ROLE_USER").claim("token_type", "REFRESH")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(KEY_BYTES)).compact();

        assertThat(provider.validateAccessToken(token)).isFalse();
        assertThat(provider.getRefreshTokenSubject(token)).isEmpty();
    }

    @Test
    @DisplayName("만료되거나 서명이 다른 토큰은 용도와 관계없이 거부해야 한다")
    void expiredAndWronglySignedTokens_AreRejected() {
        String expiredToken = Jwts.builder().subject("user@example.com")
                .claim("token_type", "REFRESH")
                .expiration(new Date(System.currentTimeMillis() - 60_000L))
                .signWith(Keys.hmacShaKeyFor(KEY_BYTES)).compact();
        byte[] otherKey = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
        String wronglySignedToken = Jwts.builder().subject("user@example.com")
                .claim("role", "ROLE_USER").claim("token_type", "ACCESS")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(otherKey)).compact();

        assertThat(provider.getRefreshTokenSubject(expiredToken)).isEmpty();
        assertThat(provider.validateAccessToken(wronglySignedToken)).isFalse();
    }
}
