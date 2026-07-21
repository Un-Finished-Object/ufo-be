package com.ufo.ufo.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.auth.dto.request.SignupRequest;
import com.ufo.ufo.domain.auth.dto.response.SignupResponse;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.InvalidTokenException;
import com.ufo.ufo.global.exception.UserNotFoundException;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
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
    private UserService userService;

    @Mock
    private InterestService interestService;

    @Mock
    private ImageService imageService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 완료 시 사용자 정보와 관심사를 저장하고 회원 역할로 승격해야 한다")
    void signup_WithGuest_PromotesRoleAndReturnsSignupResponse() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 10L);
        SignupRequest request = new SignupRequest(
                "  user01  ",
                "profiles/10/profile.png",
                List.of("빈티지", "캐주얼")
        );
        when(userService.getUserById(10L)).thenReturn(guest);
        when(userService.updateNameAndProfileImage(guest, "user01", "profiles/10/profile.png"))
                .thenAnswer(invocation -> {
                    guest.updateNameAndProfileImage("user01", "profiles/10/profile.png");
                    return guest;
                });
        when(interestService.replaceMyInterests(guest, List.of("빈티지", "캐주얼")))
                .thenReturn(List.of("빈티지", "캐주얼"));
        when(imageService.buildImageUrl("profiles/10/profile.png"))
                .thenReturn("https://cdn.example.com/profiles/10/profile.png");

        SignupResponse response = authService.signup(guest, request);

        assertThat(guest.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.userName()).isEqualTo("user01");
        assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example.com/profiles/10/profile.png");
        assertThat(response.keywords()).containsExactly("빈티지", "캐주얼");
    }

    @Test
    @DisplayName("회원가입 정보 저장에 실패하면 게스트 역할을 유지해야 한다")
    void signup_WhenInterestUpdateFails_KeepsGuestRole() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        UserFixture.setId(guest, 10L);
        SignupRequest request = new SignupRequest(
                "user01",
                "profiles/10/profile.png",
                List.of("빈티지")
        );
        when(userService.getUserById(10L)).thenReturn(guest);
        when(userService.updateNameAndProfileImage(guest, "user01", "profiles/10/profile.png"))
                .thenAnswer(invocation -> {
                    guest.updateNameAndProfileImage("user01", "profiles/10/profile.png");
                    return guest;
                });
        when(interestService.replaceMyInterests(guest, List.of("빈티지")))
                .thenThrow(new IllegalArgumentException("관심사 저장 실패"));

        assertThatThrownBy(() -> authService.signup(guest, request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(guest.getRole()).isEqualTo(Role.ROLE_GUEST);
    }

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
