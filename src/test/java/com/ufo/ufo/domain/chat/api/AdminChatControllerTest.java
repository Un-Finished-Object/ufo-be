package com.ufo.ufo.domain.chat.api;

import com.ufo.ufo.domain.chat.application.AdminChatService;
import com.ufo.ufo.domain.chat.dto.response.AdminCheckChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminDeleteChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomItemResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomListResponse;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.response.ApiResponse;
import com.ufo.ufo.support.fixture.UserFixture;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 채팅 컨트롤러 테스트")
class AdminChatControllerTest {

    @Mock
    private AdminChatService adminChatService;

    @InjectMocks
    private AdminChatController adminChatController;

    @Test
    @DisplayName("미확인 채팅방 목록 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getUnreadChatRooms_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        AdminUnreadChatRoomListResponse serviceResponse = AdminUnreadChatRoomListResponse.of(
                List.of(new AdminUnreadChatRoomItemResponse(
                        101L,
                        1L,
                        "포근한 라운드넥 스웨터",
                        "/images/patterns/1.jpg",
                        10,
                        "감사합니다. 저도 42단까지 떠볼게요.",
                        LocalDateTime.of(2026, 7, 21, 14, 32),
                        LocalDateTime.of(2026, 7, 10, 9, 0)
                )),
                1,
                3
        );

        when(adminChatService.getUnreadChatRooms(user, 1)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<AdminUnreadChatRoomListResponse>> response =
                adminChatController.getUnreadChatRooms(user, 1);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().chats()).hasSize(1);
        assertThat(response.getBody().data().page()).isEqualTo(1);
        assertThat(response.getBody().data().nextPages()).isEqualTo(3);
        verify(adminChatService).getUnreadChatRooms(user, 1);
    }

    @Test
    @DisplayName("채팅 메시지 삭제 API는 삭제 정보를 반환해야 한다")
    void deleteMessage_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        Long roomId = 19L;
        Long messageId = 38L;
        LocalDateTime deletedAt = LocalDateTime.of(2026, 2, 6, 12, 20, 10);
        AdminDeleteChatMessageResponse serviceResponse = AdminDeleteChatMessageResponse.of(roomId, messageId, deletedAt);

        when(adminChatService.deleteMessage(user, roomId, messageId)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<AdminDeleteChatMessageResponse>> response =
                adminChatController.deleteMessage(user, roomId, messageId);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().chatRoomId()).isEqualTo(roomId);
        assertThat(response.getBody().data().messageId()).isEqualTo(messageId);
        assertThat(response.getBody().data().deletedAt()).isEqualTo(deletedAt);
        verify(adminChatService).deleteMessage(user, roomId, messageId);
    }

    @Test
    @DisplayName("채팅 메시지 확인 API는 확인 정보를 반환해야 한다")
    void checkMessage_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        Long roomId = 19L;
        Long messageId = 38L;
        LocalDateTime checkedAt = LocalDateTime.of(2026, 2, 6, 12, 20, 10);
        AdminCheckChatMessageResponse serviceResponse = AdminCheckChatMessageResponse.of(roomId, messageId, checkedAt);

        when(adminChatService.checkMessage(user, roomId, messageId)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<AdminCheckChatMessageResponse>> response =
                adminChatController.checkMessage(user, roomId, messageId);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().chatRoomId()).isEqualTo(roomId);
        assertThat(response.getBody().data().messageId()).isEqualTo(messageId);
        assertThat(response.getBody().data().checkedAt()).isEqualTo(checkedAt);
        verify(adminChatService).checkMessage(user, roomId, messageId);
    }
}
