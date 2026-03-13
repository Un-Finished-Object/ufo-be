package com.ufo.ufo.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.application.ChatRoomQueryService;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomItemResponse;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomListResponse;
import com.ufo.ufo.domain.interest.application.InterestService;
import com.ufo.ufo.domain.interest.dto.request.UpdateMyInterestsRequest;
import com.ufo.ufo.domain.interest.dto.response.MyInterestsResponse;
import com.ufo.ufo.domain.scrap.application.ScrapService;
import com.ufo.ufo.domain.scrap.dto.response.MyScrapsResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
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

    @Mock
    private ScrapService scrapService;

    @Mock
    private ChatRoomQueryService chatRoomQueryService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("내 정보 조회는 로그인 사용자 정보를 그대로 응답해야 한다")
    void getMyInfo_ReturnsUserResponse() {
        User user = UserFixture.createUser();
        UserFixture.setCreatedAt(user, LocalDate.now().minusDays(10).atStartOfDay());

        ResponseEntity<ApiResponse<com.ufo.ufo.domain.user.dto.response.UserResponse>> response = userController.getMyInfo(user);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().email()).isEqualTo("test@example.com");
        assertThat(response.getBody().data().joinDate()).isEqualTo(10);
        assertThat(response.getBody().error()).isNull();
    }

    @Test
    @DisplayName("내 관심사 조회는 서비스 결과를 data에 담아 응답해야 한다")
    void getMyInterests_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        MyInterestsResponse serviceResponse = new MyInterestsResponse(List.of("빈티지", "캐주얼"));
        when(interestService.getMyInterests(user)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<MyInterestsResponse>> response = userController.getMyInterests(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().keywords()).containsExactly("빈티지", "캐주얼");
        verify(interestService).getMyInterests(user);
    }

    @Test
    @DisplayName("내 관심사 수정은 서비스 결과를 data에 담아 응답해야 한다")
    void updateMyInterests_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        UpdateMyInterestsRequest request = new UpdateMyInterestsRequest(List.of("빈티지"));
        MyInterestsResponse serviceResponse = new MyInterestsResponse(List.of("빈티지"));
        when(interestService.updateMyInterests(user, request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<MyInterestsResponse>> response = userController.updateMyInterests(user, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().keywords()).containsExactly("빈티지");
        verify(interestService).updateMyInterests(user, request);
    }

    @Test
    @DisplayName("내 찜 목록 조회는 서비스 결과를 data에 담아 응답해야 한다")
    void getMyScraps_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        MyScrapsResponse serviceResponse = MyScrapsResponse.from(List.of());
        when(scrapService.getMyScraps(user)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<MyScrapsResponse>> response = userController.getMyScraps(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().scraps()).isEmpty();
        verify(scrapService).getMyScraps(user);
    }

    @Test
    @DisplayName("내 채팅방 목록 조회는 서비스 결과를 data에 담아 응답해야 한다")
    void getMyChats_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        UserChatRoomListResponse serviceResponse = UserChatRoomListResponse.of(
                List.of(UserChatRoomItemResponse.of(1L, "가디건", "chatRoom1.png", true, false, 30))
        );
        when(chatRoomQueryService.getMyChats(user)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<UserChatRoomListResponse>> response = userController.getMyChats(user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().chats()).hasSize(1);
        assertThat(response.getBody().data().chats().get(0).chatId()).isEqualTo(1L);
        assertThat(response.getBody().data().chats().get(0).unRead()).isEqualTo(30);
        verify(chatRoomQueryService).getMyChats(user);
    }
}
