package com.ufo.ufo.domain.referral.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.request.RegisterReferralCodeRequest;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeRegistrationResponse;
import com.ufo.ufo.domain.referral.dao.ReferralRegistrationRepository;
import com.ufo.ufo.domain.referral.exception.ReferralCodeAlreadyRegisteredException;
import com.ufo.ufo.domain.referral.exception.ReferralCodeExpiredException;
import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import com.ufo.ufo.domain.referral.exception.ReferralCodeNotFoundException;
import com.ufo.ufo.domain.referral.exception.SelfReferralCodeException;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.CreditTransactionType;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 초대 서비스 테스트")
class ReferralServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReferralCodeGenerator referralCodeGenerator;

    @Mock
    private ReferralCodePersistenceService referralCodePersistenceService;

    @Mock
    private ReferralRegistrationRepository referralRegistrationRepository;

    @Mock
    private CreditService creditService;

    @InjectMocks
    private ReferralService referralService;

    @Test
    @DisplayName("친구 초대 코드 생성은 기존 코드가 없으면 새 코드를 생성해 반환해야 한다")
    void getReferralCode_GeneratesWhenMissing() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(referralCodeGenerator.generate(1L, 0)).thenReturn("UFOaB3xZ9");
        when(referralCodePersistenceService.assignAndFlush(1L, "UFOaB3xZ9"))
                .thenReturn("UFOaB3xZ9");

        ReferralCodeResponse response = referralService.getReferralCode(requestUser);

        assertThat(response.referralCode()).isEqualTo("UFOaB3xZ9");
        assertThat(response.referralCode()).hasSize(9);
    }

    @Test
    @DisplayName("초대 코드 저장 충돌 시 nonce를 변경해 다시 생성해야 한다")
    void getReferralCode_WhenSaveCollides_RetriesWithNextNonce() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(referralCodeGenerator.generate(1L, 0)).thenReturn("UFOAAAAAA");
        when(referralCodeGenerator.generate(1L, 1)).thenReturn("UFOBBBBBB");
        when(referralCodePersistenceService.assignAndFlush(1L, "UFOAAAAAA"))
                .thenThrow(new DataIntegrityViolationException("referral code collision"));
        when(referralCodePersistenceService.assignAndFlush(1L, "UFOBBBBBB"))
                .thenReturn("UFOBBBBBB");

        ReferralCodeResponse response = referralService.getReferralCode(requestUser);

        assertThat(response.referralCode()).isEqualTo("UFOBBBBBB");
        verify(referralCodePersistenceService).assignAndFlush(1L, "UFOAAAAAA");
        verify(referralCodePersistenceService).assignAndFlush(1L, "UFOBBBBBB");
    }

    @Test
    @DisplayName("HMAC 코드 생성에 실패하면 전용 예외가 발생해야 한다")
    void getReferralCode_WhenHmacGenerationFails_ThrowsException() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(referralCodeGenerator.generate(1L, 0)).thenThrow(new ReferralCodeGenerationException());

        assertThatThrownBy(() -> referralService.getReferralCode(requestUser))
                .isInstanceOf(ReferralCodeGenerationException.class);
    }

    @Test
    @DisplayName("친구 초대 코드 생성은 기존 코드가 있으면 그대로 반환해야 한다")
    void getReferralCode_ReturnsExistingCode() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        loginUser.assignReferralCode("UFOaB3xZ9");
        when(userService.getUserById(1L)).thenReturn(loginUser);

        ReferralCodeResponse response = referralService.getReferralCode(requestUser);

        assertThat(response.referralCode()).isEqualTo("UFOaB3xZ9");
        verify(referralCodeGenerator, never()).generate(eq(1L), anyInt());
        verify(referralCodePersistenceService, never()).assignAndFlush(eq(1L), anyString());
    }

    @Test
    @DisplayName("친구 초대 코드 등록은 등록자와 코드 소유자 모두에게 150 크레딧을 지급해야 한다")
    void registerReferralCode_RewardsBothUsers() {
        User requestUser = UserFixture.createUserWithId(1L);
        User referee = UserFixture.createUserWithId(1L);
        UserFixture.setCreatedAt(referee, LocalDateTime.now().minusDays(6));
        User referrer = UserFixture.createUserWithId(2L);
        when(userService.getUserById(1L)).thenReturn(referee);
        when(referralRegistrationRepository.existsByReferee_Id(1L)).thenReturn(false);
        when(userRepository.findByReferralCode("UFOaB3xZ9")).thenReturn(Optional.of(referrer));

        ReferralCodeRegistrationResponse response = referralService.registerReferralCode(
                requestUser,
                new RegisterReferralCodeRequest("UFOaB3xZ9")
        );

        assertThat(response.valid()).isTrue();
        verify(creditService).addCredits(referee, 150, CreditTransactionType.REFERRAL_BONUS);
        verify(creditService).addCredits(referrer, 150, CreditTransactionType.REFERRAL_BONUS);
    }

    @Test
    @DisplayName("친구 초대 코드는 가입 후 7일이 지나면 등록할 수 없다")
    void registerReferralCode_ExpiredUser_ThrowsException() {
        User requestUser = UserFixture.createUserWithId(1L);
        User referee = UserFixture.createUserWithId(1L);
        UserFixture.setCreatedAt(referee, LocalDateTime.now().minusDays(7).minusSeconds(1));
        when(userService.getUserById(1L)).thenReturn(referee);

        assertThatThrownBy(() -> referralService.registerReferralCode(
                requestUser,
                new RegisterReferralCodeRequest("UFOaB3xZ9")
        )).isInstanceOf(ReferralCodeExpiredException.class);
    }

    @Test
    @DisplayName("친구 초대 코드는 한 번만 등록할 수 있다")
    void registerReferralCode_AlreadyRegistered_ThrowsException() {
        User requestUser = UserFixture.createUserWithId(1L);
        User referee = UserFixture.createUserWithId(1L);
        UserFixture.setCreatedAt(referee, LocalDateTime.now().minusDays(1));
        when(userService.getUserById(1L)).thenReturn(referee);
        when(referralRegistrationRepository.existsByReferee_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> referralService.registerReferralCode(
                requestUser,
                new RegisterReferralCodeRequest("UFOaB3xZ9")
        )).isInstanceOf(ReferralCodeAlreadyRegisteredException.class);
    }

    @Test
    @DisplayName("존재하지 않는 친구 초대 코드는 등록할 수 없다")
    void registerReferralCode_UnknownCode_ThrowsException() {
        User requestUser = UserFixture.createUserWithId(1L);
        User referee = UserFixture.createUserWithId(1L);
        UserFixture.setCreatedAt(referee, LocalDateTime.now().minusDays(1));
        when(userService.getUserById(1L)).thenReturn(referee);
        when(referralRegistrationRepository.existsByReferee_Id(1L)).thenReturn(false);
        when(userRepository.findByReferralCode("NOTFOUND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> referralService.registerReferralCode(
                requestUser,
                new RegisterReferralCodeRequest("NOTFOUND")
        )).isInstanceOf(ReferralCodeNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 친구 초대 코드는 등록할 수 없다")
    void registerReferralCode_SelfCode_ThrowsException() {
        User requestUser = UserFixture.createUserWithId(1L);
        User referee = UserFixture.createUserWithId(1L);
        UserFixture.setCreatedAt(referee, LocalDateTime.now().minusDays(1));
        when(userService.getUserById(1L)).thenReturn(referee);
        when(referralRegistrationRepository.existsByReferee_Id(1L)).thenReturn(false);
        when(userRepository.findByReferralCode("UFOaB3xZ9")).thenReturn(Optional.of(referee));

        assertThatThrownBy(() -> referralService.registerReferralCode(
                requestUser,
                new RegisterReferralCodeRequest("UFOaB3xZ9")
        )).isInstanceOf(SelfReferralCodeException.class);
    }
}
