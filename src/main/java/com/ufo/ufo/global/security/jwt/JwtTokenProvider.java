package com.ufo.ufo.global.security.jwt;

import com.ufo.ufo.global.exception.InvalidAccessTokenException;
import com.ufo.ufo.global.security.types.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    public static final String BEARER_TYPE = "Bearer";
    public static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey key;

    @Getter
    @Value("${spring.jwt.access-token-expire}")
    private long accessTokenExpireTime;

    @Getter
    @Value("${spring.jwt.refresh-token-expire}")
    private long refreshTokenExpireTime;

    public JwtTokenProvider(@Value("${spring.jwt.secret}") String secret) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String email, String role) {
        return createAccessToken(email, role, null);
    }

    public String createRefreshToken(String email) {
        return createRefreshToken(email, null);
    }

    public String createAccessToken(String email, String role, String sessionId) {
        return createToken(email, role, ACCESS_TOKEN_TYPE, accessTokenExpireTime, sessionId);
    }

    public String createRefreshToken(String email, String sessionId) {
        return createToken(email, null, REFRESH_TOKEN_TYPE, refreshTokenExpireTime, sessionId);
    }

    private String createToken(String email, String role, String tokenType, long expireTime, String sessionId) {
        final Date now = new Date();
        final Date expiredDate = new Date(now.getTime() + expireTime);
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim("sid", sessionId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiredDate)
                .signWith(key)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        if (!isAccessClaims(claims)) {
            throw new InvalidAccessTokenException();
        }
        String role = claims.get("role", String.class);
        UserDetails principal = new User(claims.getSubject(), "",
                Collections.singleton(new SimpleGrantedAuthority(role)));

        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());

    }

    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    public boolean validateAccessToken(String token) {
        try {
            return isAccessClaims(parseClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Optional<String> getRefreshTokenSubject(String token) {
        return readRefreshToken(token).map(TokenClaims::email);
    }

    public Optional<TokenClaims> readAccessToken(String token) {
        return readToken(token, true);
    }

    public Optional<TokenClaims> readRefreshToken(String token) {
        return readToken(token, false);
    }

    private Optional<TokenClaims> readToken(String token, boolean access) {
        try {
            Claims claims = parseClaims(token);
            if (!(access ? isAccessClaims(claims) : isRefreshClaims(claims))) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(claims.getSubject(), claims.get("role", String.class),
                    claims.get("sid", String.class), claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isAccessClaims(Claims claims) {
        if (!hasSubject(claims) || !(claims.get("role") instanceof String role)
                || !hasTokenType(claims, ACCESS_TOKEN_TYPE)) {
            return false;
        }
        try {
            Role.valueOf(role);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isRefreshClaims(Claims claims) {
        return hasSubject(claims) && claims.get("role") == null
                && hasTokenType(claims, REFRESH_TOKEN_TYPE);
    }

    private boolean hasSubject(Claims claims) {
        Object sessionId = claims.get("sid");
        return claims.getSubject() != null && !claims.getSubject().isBlank()
                && claims.getExpiration() != null
                && (sessionId == null || sessionId instanceof String id && !id.isBlank() && id.length() <= 64);
    }

    private boolean hasTokenType(Claims claims, String expectedType) {
        return !claims.containsKey(TOKEN_TYPE_CLAIM)
                || expectedType.equals(claims.get(TOKEN_TYPE_CLAIM));
    }

    private Claims parseClaims(String token) {
        if (token == null) {
            throw new IllegalArgumentException("토큰이 필요합니다.");
        }
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String canonical = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Base64.getUrlDecoder().decode(signature));
        // 같은 서명을 다른 문자열로 표현해 구형 토큰의 해시 검사를 우회하지 못하게 한다.
        if (!signature.equals(canonical)) {
            throw new IllegalArgumentException("올바르지 않은 토큰 서명 인코딩입니다.");
        }
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
