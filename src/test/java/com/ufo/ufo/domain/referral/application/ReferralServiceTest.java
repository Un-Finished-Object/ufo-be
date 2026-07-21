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
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeValidationResponse;
import com.ufo.ufo.domain.referral.exception.ReferralCodeGenerationException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
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

    @InjectMocks
    private ReferralService referralService;

    @Test
    @DisplayName("친구 초대 코드 생성은 기존 코드가 없으면 새 코드를 생성해 반환해야 한다")
    void createReferralCode_GeneratesWhenMissing() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(referralCodeGenerator.generate(1L, 0)).thenReturn("UFOaB3xZ9");
        when(referralCodePersistenceService.assignAndFlush(1L, "UFOaB3xZ9"))
                .thenReturn("UFOaB3xZ9");

        ReferralCodeResponse response = referralService.createReferralCode(requestUser);

        assertThat(response.referralCode()).isEqualTo("UFOaB3xZ9");
        assertThat(response.referralCode()).hasSize(9);
    }

    @Test
    @DisplayName("초대 코드 저장 충돌 시 nonce를 변경해 다시 생성해야 한다")
    void createReferralCode_WhenSaveCollides_RetriesWithNextNonce() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(referralCodeGenerator.generate(1L, 0)).thenReturn("UFOAAAAAA");
        when(referralCodeGenerator.generate(1L, 1)).thenReturn("UFOBBBBBB");
        when(referralCodePersistenceService.assignAndFlush(1L, "UFOAAAAAA"))
                .thenThrow(new DataIntegrityViolationException("referral code collision"));
        when(referralCodePersistenceService.assignAndFlush(1L, "UFOBBBBBB"))
                .thenReturn("UFOBBBBBB");

        ReferralCodeResponse response = referralService.createReferralCode(requestUser);

        assertThat(response.referralCode()).isEqualTo("UFOBBBBBB");
        verify(referralCodePersistenceService).assignAndFlush(1L, "UFOAAAAAA");
        verify(referralCodePersistenceService).assignAndFlush(1L, "UFOBBBBBB");
    }

    @Test
    @DisplayName("HMAC 코드 생성에 실패하면 전용 예외가 발생해야 한다")
    void createReferralCode_WhenHmacGenerationFails_ThrowsException() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);
        when(referralCodeGenerator.generate(1L, 0)).thenThrow(new ReferralCodeGenerationException());

        assertThatThrownBy(() -> referralService.createReferralCode(requestUser))
                .isInstanceOf(ReferralCodeGenerationException.class);
    }

    @Test
    @DisplayName("친구 초대 코드 생성은 기존 코드가 있으면 그대로 반환해야 한다")
    void createReferralCode_ReturnsExistingCode() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        loginUser.assignReferralCode("UFOaB3xZ9");
        when(userService.getUserById(1L)).thenReturn(loginUser);

        ReferralCodeResponse response = referralService.createReferralCode(requestUser);

        assertThat(response.referralCode()).isEqualTo("UFOaB3xZ9");
        verify(referralCodeGenerator, never()).generate(eq(1L), anyInt());
        verify(referralCodePersistenceService, never()).assignAndFlush(eq(1L), anyString());
    }

    @Test
    @DisplayName("친구 초대 코드 확인은 코드가 존재하면 유효 응답과 소유자 닉네임을 반환해야 한다")
    void verifyReferralCode_ValidCode_ReturnsOwner() {
        User owner = UserFixture.createUser();
        when(userRepository.findByReferralCode("UFOaB3xZ9")).thenReturn(Optional.of(owner));

        ReferralCodeValidationResponse response = referralService.verifyReferralCode("UFOaB3xZ9");

        assertThat(response.valid()).isTrue();
        assertThat(response.ownerNickname()).isEqualTo(owner.getNickname());
        verify(userRepository).findByReferralCode("UFOaB3xZ9");
    }

    @Test
    @DisplayName("친구 초대 코드 확인은 코드가 없으면 유효하지 않음 응답을 반환해야 한다")
    void verifyReferralCode_InvalidCode_ReturnsFalse() {
        when(userRepository.findByReferralCode("NOTFOUND")).thenReturn(Optional.empty());

        ReferralCodeValidationResponse response = referralService.verifyReferralCode("NOTFOUND");

        assertThat(response.valid()).isFalse();
        assertThat(response.ownerNickname()).isNull();
    }
}
