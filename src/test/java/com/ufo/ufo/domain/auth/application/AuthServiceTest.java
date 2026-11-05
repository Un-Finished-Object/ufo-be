package com.ufo.ufo.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.auth.dto.request.SignupRequest;
import com.ufo.ufo.domain.auth.dto.response.SignupResponse;
import com.ufo.ufo.domain.auth.dto.response.TokenResponse;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.credit.policy.CreditPolicy;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.referral.application.ReferralService;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.exception.InvalidNicknameException;
import com.ufo.ufo.global.security.types.Role;
import com.ufo.ufo.support.fixture.UserFixture;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth 서비스 테스트")
class AuthServiceTest {

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private UserService userService;

    @Mock
    private InterestService interestService;

    @Mock
    private ImageService imageService;

    @Mock
    private CreditService creditService;

    @Mock
    private ReferralService referralService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

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
        when(userService.getUserByIdForUpdate(10L)).thenReturn(guest);
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
        verify(creditService).addCredits(guest, CreditPolicy.SIGNUP_BONUS_BALLS, CreditTransactionType.SIGNUP_BONUS);
        verify(referralService).ensureReferralCode(guest);
    }

    @Test
    @DisplayName("이미 회원인 사용자가 회원가입 정보를 다시 저장해도 회원가입 크레딧은 지급하지 않아야 한다")
    void signup_WithUser_DoesNotAwardSignupBonus() {
        User user = UserFixture.createUser("user@example.com", Role.ROLE_USER);
        UserFixture.setId(user, 10L);
        SignupRequest request = new SignupRequest(
                "user01",
                "profiles/10/profile.png",
                List.of("빈티지")
        );
        when(userService.getUserByIdForUpdate(10L)).thenReturn(user);
        when(userService.updateNameAndProfileImage(user, request.userName(), request.profileImageKey()))
                .thenReturn(user);
        when(interestService.replaceMyInterests(user, request.keywords()))
                .thenReturn(request.keywords());
        when(imageService.buildImageUrl(user.getProfileImage()))
                .thenReturn("https://cdn.example.com/profiles/10/profile.png");

        authService.signup(user, request);

        verify(creditService, never()).addCredits(user, CreditPolicy.SIGNUP_BONUS_BALLS, CreditTransactionType.SIGNUP_BONUS);
        verify(referralService).ensureReferralCode(user);
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
        when(userService.getUserByIdForUpdate(10L)).thenReturn(guest);
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
        verify(creditService, never()).addCredits(guest, CreditPolicy.SIGNUP_BONUS_BALLS, CreditTransactionType.SIGNUP_BONUS);
    }

    @Test
    @DisplayName("회원가입 닉네임은 정규화 후 2자 미만이면 예외가 발생해야 한다")
    void signup_WhenNormalizedNicknameIsTooShort_ThrowsException() {
        User guest = UserFixture.createUser("guest@example.com", Role.ROLE_GUEST);
        SignupRequest request = new SignupRequest(
                " a",
                "profiles/10/profile.png",
                List.of("빈티지")
        );

        assertThatThrownBy(() -> authService.signup(guest, request))
                .isInstanceOf(InvalidNicknameException.class);

        verifyNoInteractions(userService, interestService, imageService, creditService);
        assertThat(guest.getRole()).isEqualTo(Role.ROLE_GUEST);
    }

    @Test
    @DisplayName("토큰 재발급은 로그인 관리 서비스에서 처리한 결과를 반환해야 한다")
    void reissue_ReturnsSessionServiceResponse() {
        TokenResponse expected = new TokenResponse("new-access-token", "Bearer", 60000L);
        when(authSessionService.reissue("refresh-token")).thenReturn(expected);

        assertThat(authService.reissue("refresh-token")).isSameAs(expected);
    }

    @Test
    @DisplayName("로그아웃 시 로그인 무효화 후 HTTP 세션을 정리해야 한다")
    void logout_InvalidatesLoginAndHttpSession() {
        when(request.getSession(false)).thenReturn(session);

        authService.logout(request);

        var order = Mockito.inOrder(authSessionService, session);
        order.verify(authSessionService).logout(request);
        order.verify(session).invalidate();
    }

    @Test
    @DisplayName("로그인 무효화 저장에 실패하면 로그아웃을 성공 처리하지 않아야 한다")
    void logout_WhenInvalidationFails_DoesNotClearHttpSession() {
        Mockito.doThrow(new IllegalStateException("DB 오류"))
                .when(authSessionService).logout(request);

        assertThatThrownBy(() -> authService.logout(request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(session);
    }
}
