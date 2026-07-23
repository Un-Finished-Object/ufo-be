package com.ufo.ufo.domain.referral.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.referral.application.ReferralService;
import com.ufo.ufo.domain.referral.dto.request.RegisterReferralCodeRequest;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeResponse;
import com.ufo.ufo.domain.referral.dto.response.ReferralCodeRegistrationResponse;
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
    @DisplayName("친구 초대 코드 등록 API는 서비스 응답을 data에 담아 반환해야 한다")
    void registerReferralCode_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        RegisterReferralCodeRequest request = new RegisterReferralCodeRequest("UFOaB3xZ9");
        when(referralService.registerReferralCode(user, request))
                .thenReturn(new ReferralCodeRegistrationResponse(true));

        ResponseEntity<ApiResponse<ReferralCodeRegistrationResponse>> response =
                referralController.registerReferralCode(user, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().valid()).isTrue();
        assertThat(response.getBody().error()).isNull();
        verify(referralService).registerReferralCode(user, request);
    }

    @Test
    @DisplayName("친구 초대 코드 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getReferralCode_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        when(referralService.getReferralCode(user))
                .thenReturn(new ReferralCodeResponse("tester", "UFOaB3xZ9"));

        ResponseEntity<ApiResponse<ReferralCodeResponse>> response = referralController.getReferralCode(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().username()).isEqualTo("tester");
        assertThat(response.getBody().data().referralCode()).isEqualTo("UFOaB3xZ9");
        assertThat(response.getBody().error()).isNull();
        verify(referralService).getReferralCode(user);
    }
}
