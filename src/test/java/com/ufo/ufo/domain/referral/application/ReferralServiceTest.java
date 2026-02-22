package com.ufo.ufo.domain.referral.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeValidationResponse;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 초대 서비스 테스트")
class ReferralServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReferralService referralService;

    @Test
    @DisplayName("친구 초대 코드 생성은 기존 코드가 없으면 새 코드를 생성해 반환해야 한다")
    void createReferralCode_GeneratesWhenMissing() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(loginUser);

        ReferralCodeResponse response = referralService.createReferralCode(requestUser);

        assertThat(response.referralCode()).isNotBlank();
        assertThat(response.referralCode()).hasSize(10);
        assertThat(response.referralCode()).isEqualTo(response.referralCode().toUpperCase());
    }

    @Test
    @DisplayName("친구 초대 코드 생성은 기존 코드가 있으면 그대로 반환해야 한다")
    void createReferralCode_ReturnsExistingCode() {
        User requestUser = UserFixture.createUserWithId(1L);
        User loginUser = UserFixture.createUserWithId(1L);
        loginUser.assignReferralCode("INVITE2026");
        when(userService.getUserById(1L)).thenReturn(loginUser);

        ReferralCodeResponse response = referralService.createReferralCode(requestUser);

        assertThat(response.referralCode()).isEqualTo("INVITE2026");
    }

    @Test
    @DisplayName("친구 초대 코드 확인은 코드가 존재하면 유효 응답과 소유자 닉네임을 반환해야 한다")
    void verifyReferralCode_ValidCode_ReturnsOwner() {
        User owner = UserFixture.createUser();
        when(userRepository.findByReferralCode("INVITE2026")).thenReturn(Optional.of(owner));

        ReferralCodeValidationResponse response = referralService.verifyReferralCode("INVITE2026");

        assertThat(response.valid()).isTrue();
        assertThat(response.ownerNickname()).isEqualTo(owner.getNickname());
        verify(userRepository).findByReferralCode("INVITE2026");
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
