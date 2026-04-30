package com.ufo.ufo.domain.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.application.ChatMessageService;
import com.ufo.ufo.domain.chat.application.ChatRoomStatusService;
import com.ufo.ufo.domain.chat.dto.request.UpdateChatRoomStatusRequest;
import com.ufo.ufo.domain.chat.dto.response.ChatMessageItemResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatMessagesResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomStatusResponse;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 컨트롤러 테스트")
class ChatControllerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatRoomStatusService chatRoomStatusService;

    @InjectMocks
    private ChatController chatController;

    @Test
    @DisplayName("채팅 메시지 조회 API는 서비스 응답을 data에 담아 반환해야 한다")
    void getMessages_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        Long roomId = 10L;
        Long messageId = 50L;
        ChatMessagesResponse serviceResponse = new ChatMessagesResponse(
                38L,
                true,
                20L,
                List.of(new ChatMessageItemResponse(
                        1L,
                        "테스터",
                        49L,
                        "안녕하세요",
                        null,
                        null,
                        LocalDateTime.of(2026, 3, 9, 13, 20, 10)
                ))
        );
        when(chatMessageService.getMessages(user, roomId, messageId)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<ChatMessagesResponse>> response =
                chatController.getMessages(user, roomId, messageId);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().lastMessageId()).isEqualTo(38L);
        assertThat(response.getBody().data().hasNext()).isTrue();
        assertThat(response.getBody().data().nextMessageId()).isEqualTo(20L);
        assertThat(response.getBody().data().messages()).hasSize(1);
        assertThat(response.getBody().data().messages().getFirst().senderId()).isEqualTo(1L);
        assertThat(response.getBody().data().messages().getFirst().senderName()).isEqualTo("테스터");
        assertThat(response.getBody().data().messages().getFirst().messageId()).isEqualTo(49L);
        assertThat(response.getBody().data().messages().getFirst().replyMessageId()).isNull();
        assertThat(response.getBody().error()).isNull();
        verify(chatMessageService).getMessages(user, roomId, messageId);
    }

    @Test
    @DisplayName("채팅방 상태 변경 API는 변경된 상태를 반환해야 한다")
    void updateStatus_ReturnsServiceResponse() {
        User user = UserFixture.createUserWithId(1L);
        Long roomId = 10L;
        UpdateChatRoomStatusRequest request = new UpdateChatRoomStatusRequest(true, false);
        ChatRoomStatusResponse serviceResponse = ChatRoomStatusResponse.of(roomId, true, false);

        when(chatRoomStatusService.updateStatus(user, roomId, request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<ChatRoomStatusResponse>> response =
                chatController.updateStatus(user, roomId, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().chatId()).isEqualTo(roomId);
        assertThat(response.getBody().data().favorite()).isTrue();
        assertThat(response.getBody().data().isHidden()).isFalse();
        assertThat(response.getBody().error()).isNull();
        verify(chatRoomStatusService).updateStatus(user, roomId, request);
    }
}
