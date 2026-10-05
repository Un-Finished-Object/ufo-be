package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.support.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 이벤트 전송 테스트")
class ChatSocketEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("오류 이벤트는 요청 식별자와 함께 해당 채팅방으로 전송해야 한다")
    void sendsErrorToRoomWithClientMessageId() {
        var publisher = new ChatSocketEventPublisher(messagingTemplate);

        publisher.sendError(10L, "CHAT_ROOM_FORBIDDEN", "접근 권한이 없는 채팅방입니다.", "temp-1");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/10"), eventCaptor.capture());
        ChatSocketEvent<?> event = (ChatSocketEvent<?>) eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.ERROR);
        assertThat(event.roomId()).isEqualTo(10L);
        assertThat((ChatErrorPayload) event.payload()).isEqualTo(new ChatErrorPayload(
                "CHAT_ROOM_FORBIDDEN", "접근 권한이 없는 채팅방입니다.", "temp-1"
        ));
    }

    @Test
    @DisplayName("거부된 채팅 요청은 채팅방과 요청 식별자를 기록하고 오류 내용은 로그에 넣지 않아야 한다")
    void recordsRejectedRequestWithoutErrorMessage() {
        var publisher = new ChatSocketEventPublisher(messagingTemplate);
        try (var capture = new LogCapture(ChatSocketEventPublisher.class)) {
            publisher.sendError(10L, "CHAT_ROOM_FORBIDDEN", "private-error-message", "message-123");
            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getFormattedMessage())
                    .contains("10", "CHAT_ROOM_FORBIDDEN", "message-123").doesNotContain("private-error-message");
        }
    }

    @Test
    @DisplayName("채팅 요청 식별자에 개인정보나 줄바꿈이 포함되면 로그에서 생략해야 한다")
    void omitsUnsafeClientMessageId() {
        var publisher = new ChatSocketEventPublisher(messagingTemplate);
        try (var capture = new LogCapture(ChatSocketEventPublisher.class)) {
            publisher.sendError(10L, "INVALID_MESSAGE_TEXT", "오류 내용", "private@example.com\r\nforged");
            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getFormattedMessage())
                    .contains("10", "INVALID_MESSAGE_TEXT").doesNotContain("private@example.com", "forged", "오류 내용");
        }
    }
}
