package com.ufo.ufo.domain.auth.application;

import com.ufo.ufo.domain.auth.dao.AuthSessionRepository;
import com.ufo.ufo.domain.auth.dao.RevokedAccessTokenRepository;
import com.ufo.ufo.domain.auth.domain.AuthSession;
import com.ufo.ufo.domain.auth.domain.RevokedAccessToken;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidAccessTokenException;
import com.ufo.ufo.global.exception.InvalidTokenException;
import com.ufo.ufo.global.exception.UserNotFoundException;
import com.ufo.ufo.global.security.jwt.AccessTokenContext;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.jwt.TokenClaims;
import com.ufo.ufo.global.security.oauth.OAuthCookieManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final RevokedAccessTokenRepository revokedTokenRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String issueRefreshToken(String email) {
        User user = lockUser(email);
        String sessionId = UUID.randomUUID().toString();
        String token = tokenProvider.createRefreshToken(email, sessionId);
        TokenClaims claims = tokenProvider.readRefreshToken(token).orElseThrow(InvalidTokenException::new);
        sessionRepository.saveAndFlush(new AuthSession(sessionId, user, claims.expiresAt()));
        return token;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TokenResponse reissue(String refreshToken) {
        TokenClaims claims = tokenProvider.readRefreshToken(refreshToken).orElseThrow(InvalidTokenException::new);
        User user = lockUser(claims.email());
        AuthSession session = refreshSession(claims, refreshToken, user);
        if (!session.canRefresh(Instant.now())) {
            throw new InvalidTokenException();
        }
        String token = tokenProvider.createAccessToken(user.getEmail(), user.getRoleKey(), session.getId());
        TokenClaims access = tokenProvider.readAccessToken(token).orElseThrow(InvalidAccessTokenException::new);
        session.retainUntil(access.expiresAt());
        return new TokenResponse(token, JwtTokenProvider.BEARER_TYPE, tokenProvider.getAccessTokenExpireTime());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void logout(HttpServletRequest request) {
        String token = tokenProvider.resolveToken(request);
        TokenClaims access = tokenProvider.readAccessToken(token).orElseThrow(InvalidAccessTokenException::new);
        User user = lockUser(access.email());
        Instant now = Instant.now();
        if (access.sessionId() == null) {
            String hash = tokenHash(token);
            if (!revokedTokenRepository.existsById(hash)) {
                revokedTokenRepository.save(new RevokedAccessToken(hash, access.expiresAt()));
            }
        } else {
            sessionRepository.findByIdForUpdate(access.sessionId())
                    .filter(session -> session.getUser().getId().equals(user.getId()))
                    .ifPresent(session -> session.revoke(now));
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (OAuthCookieManager.REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    revokeRefreshToken(cookie.getValue(), user, now);
                }
            }
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Authentication authenticateAccessToken(String token) {
        TokenClaims claims = tokenProvider.readAccessToken(token).orElseThrow(InvalidAccessTokenException::new);
        AccessTokenContext context = new AccessTokenContext(
                claims.email(), claims.sessionId(), tokenHash(token), claims.expiresAt());
        if (!isActive(context)) {
            throw new InvalidAccessTokenException();
        }
        Authentication authentication = tokenProvider.getAuthentication(token);
        ((AbstractAuthenticationToken) authentication).setDetails(context);
        return authentication;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean isActive(AccessTokenContext context) {
        Instant now = Instant.now();
        if (!now.isBefore(context.expiresAt())) {
            return false;
        }
        if (context.sessionId() == null) {
            return !revokedTokenRepository.existsById(context.tokenHash());
        }
        return sessionRepository.existsByIdAndUser_EmailAndRevokedAtIsNullAndRetainUntilAfter(
                context.sessionId(), context.email(), now);
    }

    private void revokeRefreshToken(String token, User user, Instant now) {
        tokenProvider.readRefreshToken(token)
                .filter(claims -> claims.email().equals(user.getEmail()))
                .ifPresent(claims -> {
                    try {
                        refreshSession(claims, token, user).revoke(now);
                    } catch (InvalidTokenException ignored) {
                        // 유효하지 않은 쿠키 때문에 정상 Bearer 토큰의 로그아웃을 취소하지 않는다.
                    }
                });
    }

    private AuthSession refreshSession(TokenClaims claims, String token, User user) {
        String id = claims.sessionId() == null ? tokenHash(token) : claims.sessionId();
        AuthSession session = sessionRepository.findByIdForUpdate(id).orElseGet(() -> {
            if (claims.sessionId() != null) {
                throw new InvalidTokenException();
            }
            sessionRepository.saveAndFlush(new AuthSession(id, user, claims.expiresAt()));
            return sessionRepository.findByIdForUpdate(id).orElseThrow(InvalidTokenException::new);
        });
        if (!session.getUser().getId().equals(user.getId())
                || !session.getRefreshExpiresAt().equals(claims.expiresAt())) {
            throw new InvalidTokenException();
        }
        return session;
    }

    private User lockUser(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        return userRepository.findByIdForUpdate(user.getId()).orElseThrow(UserNotFoundException::new);
    }

    private static String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.");
        }
    }
}
