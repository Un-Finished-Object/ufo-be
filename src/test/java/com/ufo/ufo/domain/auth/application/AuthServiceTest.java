package com.ufo.ufo.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidTokenException;
import com.ufo.ufo.global.exception.UserNotFoundException;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth 서비스 테스트")
class AuthServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("유효한 refresh token이면 access token을 재발급해야 한다")
    void reissue_WithValidRefreshToken_ReturnsTokenResponse() {
        String refreshToken = "refresh-token";
        String email = "user@example.com";
        User user = UserFixture.createUser(email, Role.ROLE_USER);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(refreshToken)).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(email, user.getRoleKey())).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenExpireTime()).thenReturn(3_600_000L);

        TokenResponse response = authService.reissue(refreshToken);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3_600_000L);
    }

    @Test
    @DisplayName("유효하지 않은 refresh token이면 InvalidTokenException이 발생해야 한다")
    void reissue_WithInvalidRefreshToken_ThrowsInvalidTokenException() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.reissue("bad-token"))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("토큰 주체 사용자 정보가 없으면 UserNotFoundException이 발생해야 한다")
    void reissue_WhenUserNotFound_ThrowsUserNotFoundException() {
        String refreshToken = "refresh-token";
        String email = "missing@example.com";

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(refreshToken)).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("로그아웃 시 세션이 있으면 invalidate를 호출해야 한다")
    void logout_WhenSessionExists_InvalidatesSession() {
        when(request.getSession(false)).thenReturn(session);

        authService.logout(request);

        verify(session).invalidate();
    }

    @Test
    @DisplayName("로그아웃 시 세션이 없으면 예외 없이 종료해야 한다")
    void logout_WhenSessionAbsent_DoesNothing() {
        when(request.getSession(false)).thenReturn(null);

        authService.logout(request);

        verify(request).getSession(false);
    }
}
