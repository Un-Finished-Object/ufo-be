package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.UserFixture;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 WebSocket 서비스 테스트")
class ChatWebSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatWebSocketService chatWebSocketService;

    @Test
    @DisplayName("메시지 전송 시 해당 채팅방으로 MESSAGE_CREATED 이벤트를 브로드캐스트해야 한다")
    void publishMessage_BroadcastsMessageCreatedEvent() {
        Long roomId = 10L;
        String userEmail = "test@example.com";
        User user = UserFixture.createUser(userEmail, com.ufo.ufo.global.security.types.Role.ROLE_USER);
        UserFixture.setId(user, 21L);
        Principal principal = () -> userEmail;

        when(patternRepository.existsById(roomId)).thenReturn(true);
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));

        chatWebSocketService.publishMessage(principal,
                new ChatMessageSendRequest(roomId, " 안녕하세요 ", "temp-123456"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/10"), payloadCaptor.capture());

        ChatSocketEvent<?> event = (ChatSocketEvent<?>) payloadCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.MESSAGE_CREATED);
        assertThat(event.roomId()).isEqualTo(roomId);

        ChatMessageCreatedPayload payload = (ChatMessageCreatedPayload) event.payload();
        assertThat(payload.messageId()).isNotNull();
        assertThat(payload.clientMessageId()).isEqualTo("temp-123456");
        assertThat(payload.senderId()).isEqualTo(21L);
        assertThat(payload.senderName()).isEqualTo(user.getNickname());
        assertThat(payload.text()).isEqualTo("안녕하세요");
        assertThat(payload.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 채팅방으로 메시지 전송 시 ERROR 이벤트를 브로드캐스트해야 한다")
    void publishMessage_InvalidRoom_BroadcastsErrorEvent() {
        Long roomId = 999L;
        Principal principal = () -> "test@example.com";
        when(patternRepository.existsById(roomId)).thenReturn(false);

        chatWebSocketService.publishMessage(principal,
                new ChatMessageSendRequest(roomId, "안녕하세요", "temp-err-1"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/999"), payloadCaptor.capture());
        verify(userRepository, never()).findByEmail(any());

        ChatSocketEvent<?> event = (ChatSocketEvent<?>) payloadCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.ERROR);
        assertThat(event.roomId()).isEqualTo(roomId);

        ChatErrorPayload payload = (ChatErrorPayload) event.payload();
        assertThat(payload.code()).isEqualTo("CHAT_ROOM_NOT_FOUND");
        assertThat(payload.message()).isEqualTo("존재하지 않는 채팅방입니다.");
        assertThat(payload.clientMessageId()).isEqualTo("temp-err-1");
    }
}
