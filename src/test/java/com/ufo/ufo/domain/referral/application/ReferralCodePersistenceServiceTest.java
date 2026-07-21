package com.ufo.ufo.domain.referral.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
@DisplayName("친구 초대 코드 저장 서비스 테스트")
class ReferralCodePersistenceServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReferralCodePersistenceService referralCodePersistenceService;

    @Test
    @DisplayName("사용자 행을 잠근 뒤 초대 코드를 저장하고 flush해야 한다")
    void assignAndFlush_WhenCodeIsMissing_AssignsAndFlushesCode() {
        User user = UserFixture.createUserWithId(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        String referralCode = referralCodePersistenceService.assignAndFlush(1L, "UFOaB3xZ9");

        assertThat(referralCode).isEqualTo("UFOaB3xZ9");
        assertThat(user.getReferralCode()).isEqualTo("UFOaB3xZ9");
        verify(userRepository).findByIdForUpdate(1L);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    @DisplayName("잠근 사용자에게 기존 초대 코드가 있으면 해당 코드를 반환해야 한다")
    void assignAndFlush_WhenCodeExists_ReturnsExistingCode() {
        User user = UserFixture.createUserWithId(1L);
        user.assignReferralCode("UFOEXIST1");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        String referralCode = referralCodePersistenceService.assignAndFlush(1L, "UFONEW001");

        assertThat(referralCode).isEqualTo("UFOEXIST1");
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}
