package com.ufo.ufo.domain.referral.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.referral.application.ReferralService;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeValidationResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("친구 초대 컨트롤러 테스트")
class ReferralControllerTest {

    @Mock
    private ReferralService referralService;

    @InjectMocks
    private ReferralController referralController;

    @Test
    @DisplayName("친구 초대 코드 생성 API는 서비스 응답을 data에 담아 반환해야 한다")
    void createReferralCode_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(referralService.createReferralCode(user)).thenReturn(new ReferralCodeResponse("INVITE2026"));

        ResponseEntity<ApiResponse<ReferralCodeResponse>> response = referralController.createReferralCode(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().referralCode()).isEqualTo("INVITE2026");
        assertThat(response.getBody().error()).isNull();
        verify(referralService).createReferralCode(user);
    }

    @Test
    @DisplayName("친구 초대 코드 확인 API는 서비스 응답을 data에 담아 반환해야 한다")
    void verifyReferralCode_ReturnsServiceResponse() {
        when(referralService.verifyReferralCode("INVITE2026"))
                .thenReturn(new ReferralCodeValidationResponse(true, "tester"));

        ResponseEntity<ApiResponse<ReferralCodeValidationResponse>> response =
                referralController.verifyReferralCode("INVITE2026");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().valid()).isTrue();
        assertThat(response.getBody().data().ownerNickname()).isEqualTo("tester");
        assertThat(response.getBody().error()).isNull();
        verify(referralService).verifyReferralCode("INVITE2026");
    }
}
