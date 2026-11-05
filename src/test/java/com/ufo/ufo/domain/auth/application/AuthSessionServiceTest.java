package com.ufo.ufo.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.auth.dao.AuthSessionRepository;
import com.ufo.ufo.domain.auth.dao.RevokedAccessTokenRepository;
import com.ufo.ufo.domain.auth.domain.AuthSession;
import com.ufo.ufo.domain.auth.domain.RevokedAccessToken;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidAccessTokenException;
import com.ufo.ufo.global.exception.InvalidTokenException;
import com.ufo.ufo.global.exception.UserNotFoundException;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("로그인별 토큰 무효화 서비스 테스트")
class AuthSessionServiceTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final Map<String, AuthSession> sessions = new HashMap<>();
    private final Map<String, RevokedAccessToken> revoked = new HashMap<>();
    private final User user = UserFixture.createUserWithId(1L);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthSessionRepository repository = mock(AuthSessionRepository.class);
    private final RevokedAccessTokenRepository revokedRepository = mock(RevokedAccessTokenRepository.class);
    private JwtTokenProvider provider;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET);
        ReflectionTestUtils.setField(provider, "accessTokenExpireTime", 120000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpireTime", 60000L);
        service = new AuthSessionService(provider, users, repository, revokedRepository);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(repository.findByIdForUpdate(anyString()))
                .thenAnswer(call -> Optional.ofNullable(sessions.get(call.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            AuthSession session = call.getArgument(0);
            sessions.put(session.getId(), session);
            return session;
        });
        when(repository.existsByIdAndUser_EmailAndRevokedAtIsNullAndRetainUntilAfter(anyString(), anyString(), any()))
                .thenAnswer(call -> {
                    AuthSession session = sessions.get(call.getArgument(0));
                    Instant now = call.getArgument(2);
                    return session != null && session.getRevokedAt() == null
                            && session.getUser().getEmail().equals(call.getArgument(1))
                            && session.getRetainUntil().isAfter(now);
                });
        when(revokedRepository.existsById(anyString())).thenAnswer(call -> revoked.containsKey(call.getArgument(0)));
        when(revokedRepository.save(any())).thenAnswer(call -> {
            RevokedAccessToken record = call.getArgument(0);
            revoked.put(record.getTokenHash(), record);
            return record;
        });
    }

    @Test
    @DisplayName("로그아웃한 Refresh Token과 해당 로그인의 Access Token은 모두 거부해야 한다")
    void logout_PreventsRefreshTokenReuse() {
        String refresh = service.issueRefreshToken(user.getEmail());
        String first = service.reissue(refresh).accessToken();
        String second = service.reissue(refresh).accessToken();
        assertThat(service.authenticateAccessToken(first).getName()).isEqualTo(user.getEmail());

        service.logout(request(first, refresh));

        assertThatThrownBy(() -> service.reissue(refresh)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.authenticateAccessToken(first))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> service.authenticateAccessToken(second))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("같은 시각의 로그인도 구분하며 한 기기의 로그아웃은 다른 로그인에 영향을 주지 않아야 한다")
    void logout_DoesNotInvalidateAnotherLogin() {
        String first = service.issueRefreshToken(user.getEmail());
        String second = service.issueRefreshToken(user.getEmail());
        assertThat(first).isNotEqualTo(second);
        String firstAccess = service.reissue(first).accessToken();

        service.logout(request(firstAccess, first));

        assertThat(service.authenticateAccessToken(service.reissue(second).accessToken()).isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 Access Token이 가리키는 로그인을 무효화해야 한다")
    void logout_WithoutCookie_InvalidatesLogin() {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();

        service.logout(request(access, null));

        assertThatThrownBy(() -> service.reissue(refresh)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Bearer와 같은 사용자 쿠키가 서로 다른 로그인이라면 제시된 두 로그인만 무효화해야 한다")
    void logout_WithDifferentLoginCookie_RevokesPresentedLogins() {
        String first = service.issueRefreshToken(user.getEmail());
        String second = service.issueRefreshToken(user.getEmail());
        String third = service.issueRefreshToken(user.getEmail());

        service.logout(request(service.reissue(first).accessToken(), second));

        assertThatThrownBy(() -> service.reissue(first)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.reissue(second)).isInstanceOf(InvalidTokenException.class);
        assertThat(service.reissue(third).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("다른 사용자의 Refresh Token 쿠키로 해당 사용자의 로그인을 무효화하지 않아야 한다")
    void logout_IgnoresOtherUsersCookie() {
        User other = UserFixture.createUserWithId(2L);
        ReflectionTestUtils.setField(other, "email", "other@example.com");
        when(users.findByEmail(other.getEmail())).thenReturn(Optional.of(other));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(other));
        String otherRefresh = service.issueRefreshToken(other.getEmail());
        String refresh = service.issueRefreshToken(user.getEmail());

        service.logout(request(service.reissue(refresh).accessToken(), otherRefresh));

        assertThat(service.reissue(otherRefresh).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("구형 Refresh Token은 재발급 시 로그인에 연결되고 로그아웃 후 되살아나지 않아야 한다")
    void legacyRefresh_IsAdoptedOnceAndRemainsRevoked() {
        String refresh = provider.createRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        assertThat(provider.readAccessToken(access).orElseThrow().sessionId()).hasSize(64);

        service.logout(request(access, refresh));
        var otherServer = new AuthSessionService(provider, users, repository, revokedRepository);

        assertThatThrownBy(() -> otherServer.reissue(refresh)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> otherServer.authenticateAccessToken(access))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("로그아웃에 제시된 구형 Access Token은 해시로 기록하고 재사용을 거부해야 한다")
    void logout_RevokesPresentedLegacyAccessToken() {
        String access = provider.createAccessToken(user.getEmail(), "ROLE_USER");

        service.logout(request(access, null));
        service.logout(request(access, null));

        assertThat(revoked).hasSize(1);
        assertThat(revoked.keySet()).allSatisfy(hash -> assertThat(hash).hasSize(64).doesNotContain(access));
        assertThatThrownBy(() -> service.authenticateAccessToken(access))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("구형 Refresh Token의 서명 표현을 바꿔도 로그아웃을 우회하지 못해야 한다")
    void legacyRefresh_EquivalentSignatureCannotRestoreSession(boolean padding) {
        String refresh = provider.createRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        service.logout(request(access, refresh));

        assertThatThrownBy(() -> service.reissue(equivalentSignature(refresh, padding)))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(sessions).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("구형 Access Token의 서명 표현을 바꿔도 로그아웃을 우회하지 못해야 한다")
    void legacyAccess_EquivalentSignatureCannotBypassRevocation(boolean padding) {
        String access = provider.createAccessToken(user.getEmail(), "ROLE_USER");
        service.logout(request(access, null));

        assertThatThrownBy(() -> service.authenticateAccessToken(equivalentSignature(access, padding)))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    private String equivalentSignature(String token, boolean padding) {
        int separator = token.lastIndexOf('.') + 1;
        String signature = token.substring(separator);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        assertThat(signature.length() % 4).isEqualTo(3);
        int last = alphabet.indexOf(signature.charAt(signature.length() - 1));
        String variant = padding ? signature + "="
                : signature.substring(0, signature.length() - 1) + alphabet.charAt(last ^ 1);
        assertThat(Base64.getUrlDecoder().decode(variant))
                .isEqualTo(Base64.getUrlDecoder().decode(signature));
        return token.substring(0, separator) + variant;
    }

    @Test
    @DisplayName("Refresh Token보다 늦게 만료되는 Access Token까지 로그인 기록을 보관해야 한다")
    void reissue_ExtendsRetentionThroughAccessExpiry() {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        AuthSession session = sessions.get(provider.readAccessToken(access).orElseThrow().sessionId());

        assertThat(session.getRetainUntil()).isAfter(session.getRefreshExpiresAt());
        assertThat(session.getRetainUntil()).isEqualTo(provider.readAccessToken(access).orElseThrow().expiresAt());
    }

    @Test
    @DisplayName("알 수 없는 로그인 식별자의 토큰은 서명이 맞아도 거부해야 한다")
    void unknownSession_IsRejected() {
        String access = provider.createAccessToken(user.getEmail(), "ROLE_USER", "unknown");
        String refresh = provider.createRefreshToken(user.getEmail(), "unknown");

        assertThatThrownBy(() -> service.authenticateAccessToken(access))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> service.reissue(refresh)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Access Token을 재발급에 사용하거나 사용자가 없으면 재발급하지 않아야 한다")
    void invalidRefreshAndMissingUser_AreRejected() {
        String access = provider.createAccessToken(user.getEmail(), "ROLE_USER");
        assertThatThrownBy(() -> service.reissue(access)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.reissue("invalid")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.reissue(provider.createRefreshToken("missing@example.com")))
                .isInstanceOf(UserNotFoundException.class);
    }

    private MockHttpServletRequest request(String access, String refresh) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + access);
        if (refresh != null) {
            request.setCookies(new Cookie("refresh_token", refresh));
        }
        return request;
    }

    @Test
    @DisplayName("유효하지 않은 로그인 쿠키가 있어도 정상 Access Token의 로그아웃은 처리해야 한다")
    void logout_IgnoresUnknownSessionCookie() {
        String refresh = service.issueRefreshToken(user.getEmail());
        String access = service.reissue(refresh).accessToken();
        String unknown = provider.createRefreshToken(user.getEmail(), "unknown-session");

        service.logout(request(access, unknown));

        assertThatThrownBy(() -> service.reissue(refresh)).isInstanceOf(InvalidTokenException.class);
    }
}
