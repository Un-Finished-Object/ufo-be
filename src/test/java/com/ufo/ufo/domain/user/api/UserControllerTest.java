package com.ufo.ufo.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.interest.dto.request.UpdateMyInterestsRequest;
import com.ufo.ufo.domain.interest.dto.response.MyInterestsResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("User 컨트롤러 테스트")
class UserControllerTest {

    @Mock
    private InterestService interestService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("내 정보 조회는 로그인 사용자 정보를 그대로 응답해야 한다")
    void getMyInfo_ReturnsUserResponse() {
        User user = UserFixture.createUser();

        ResponseEntity<ApiResponse<com.ufo.ufo.domain.user.dto.response.UserResponse>> response = userController.getMyInfo(user);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().email()).isEqualTo("test@example.com");
        assertThat(response.getBody().error()).isNull();
    }

    @Test
    @DisplayName("내 관심사 조회는 서비스 결과를 data에 담아 응답해야 한다")
    void getMyInterests_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        MyInterestsResponse serviceResponse = new MyInterestsResponse(List.of("YARN", "DIY"));
        when(interestService.getMyInterests(user)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<MyInterestsResponse>> response = userController.getMyInterests(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().keywords()).containsExactly("YARN", "DIY");
        verify(interestService).getMyInterests(user);
    }

    @Test
    @DisplayName("내 관심사 수정은 서비스 결과를 data에 담아 응답해야 한다")
    void updateMyInterests_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        UpdateMyInterestsRequest request = new UpdateMyInterestsRequest(List.of("yarn"));
        MyInterestsResponse serviceResponse = new MyInterestsResponse(List.of("YARN"));
        when(interestService.updateMyInterests(user, request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<MyInterestsResponse>> response = userController.updateMyInterests(user, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().keywords()).containsExactly("YARN");
        verify(interestService).updateMyInterests(user, request);
    }
}
