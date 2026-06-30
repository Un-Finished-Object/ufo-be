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
import com.ufo.ufo.domain.user.application.UserProjectService;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dto.request.UpdateMyInfoRequest;
import com.ufo.ufo.domain.user.dto.response.UpdateMyInfoResponse;
import com.ufo.ufo.domain.user.dto.response.PurchasedProjectsResponse;
import com.ufo.ufo.domain.user.dto.response.UserResponse;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Mock
    private UserProjectService userProjectService;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("내 정보 조회는 로그인 사용자 정보를 그대로 응답해야 한다")
    void getMyInfo_ReturnsUserResponse() {
        User user = UserFixture.createUser();
        UserFixture.setId(user, 10L);
        UserFixture.setCreatedAt(user, LocalDate.now().minusDays(10).atStartOfDay());

        ResponseEntity<ApiResponse<UserResponse>> response = userController.getMyInfo(user);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().userId()).isEqualTo(10L);
        assertThat(response.getBody().data().email()).isEqualTo("test@example.com");
        assertThat(response.getBody().data().joinDate()).isEqualTo(10);
        assertThat(response.getBody().error()).isNull();
    }

    @Test
    @DisplayName("내 정보 수정은 서비스 결과를 data에 담아 응답해야 한다")
    void updateMyInfo_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        UserFixture.setId(user, 10L);
        UpdateMyInfoRequest request = new UpdateMyInfoRequest("newName", "https://example.com/new.png");
        UpdateMyInfoResponse serviceResponse = new UpdateMyInfoResponse(10L, "newName", "https://example.com/new.png");
        when(userService.updateMyInfo(user, request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<UpdateMyInfoResponse>> response = userController.updateMyInfo(user, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().userId()).isEqualTo(10L);
        assertThat(response.getBody().data().nickname()).isEqualTo("newName");
        assertThat(response.getBody().data().profileImage()).isEqualTo("https://example.com/new.png");
        verify(userService).updateMyInfo(user, request);
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
        MyScrapsResponse serviceResponse = MyScrapsResponse.from(List.of(), 1, 0);
        when(scrapService.getMyScraps(user, 1)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<MyScrapsResponse>> response = userController.getMyScraps(user, 1);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().items()).isEmpty();
        verify(scrapService).getMyScraps(user, 1);
    }

    @Test
    @DisplayName("내 채팅방 목록 조회는 서비스 결과를 data에 담아 응답해야 한다")
    void getMyChats_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        UserChatRoomListResponse serviceResponse = UserChatRoomListResponse.of(
                List.of(UserChatRoomItemResponse.of(
                        10L,
                        1L,
                        "가디건",
                        "chatRoom1.png",
                        true,
                        false,
                        30,
                        2,
                        "hello",
                        LocalDateTime.of(2026, 4, 1, 0, 0)
                )),
                1,
                0
        );
        when(chatRoomQueryService.getMyChats(user, 1)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<UserChatRoomListResponse>> response = userController.getMyChats(user, 1);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().chats()).hasSize(1);
        assertThat(response.getBody().data().chats().getFirst().chatId()).isEqualTo(1L);
        assertThat(response.getBody().data().chats().getFirst().unRead()).isEqualTo(30);
        assertThat(response.getBody().data().chats().getFirst().patternId()).isEqualTo(10L);
        assertThat(response.getBody().data().chats().getFirst().userCount()).isEqualTo(2);
        assertThat(response.getBody().data().chats().getFirst().lastMessage()).isEqualTo("hello");
        assertThat(response.getBody().data().page()).isEqualTo(1);
        assertThat(response.getBody().data().nextPage()).isEqualTo(0);
        verify(chatRoomQueryService).getMyChats(user, 1);
    }

    @Test
    @DisplayName("구매한 프로젝트 목록 조회는 서비스 결과를 data에 담아 응답해야 한다")
    void getMyProjects_ReturnsServiceResponse() {
        User user = UserFixture.createUser();
        PurchasedProjectsResponse serviceResponse = PurchasedProjectsResponse.from(List.of(), 0);
        when(userProjectService.getPurchasedProjects(user, 1)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<PurchasedProjectsResponse>> response = userController.getMyProjects(user, 1);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().projects()).isEmpty();
        assertThat(response.getBody().data().nextPage()).isEqualTo(0);
        verify(userProjectService).getPurchasedProjects(user, 1);
    }
}
