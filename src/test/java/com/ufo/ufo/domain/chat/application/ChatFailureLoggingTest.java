package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.support.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("채팅 처리 실패 로그 테스트")
class ChatFailureLoggingTest {

    @Test
    @DisplayName("메시지 처리 실패는 채팅방과 요청 식별자를 기록하고 원래 예외를 유지해야 한다")
    void recordsMessageFailureWithoutMessageBody() {
        var sender = mock(ChatMessageSendService.class);
        var request = new ChatMessageSendRequest(10L, "private-message", "message-123", false, null);
        var failure = new IllegalStateException("token-secret");
        doThrow(failure).when(sender).send(null, request);
        var service = new ChatWebSocketService(sender, mock(ChatReadUpdateService.class));

        try (var capture = new LogCapture(ChatWebSocketService.class)) {
            assertThatThrownBy(() -> service.publishMessage(null, request)).isSameAs(failure);
            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getFormattedMessage())
                    .contains("10", "message-123", "IllegalStateException")
                    .doesNotContain("private-message", "token-secret");
        }
    }

    @Test
    @DisplayName("읽음 처리 실패는 채팅방을 기록하고 원래 예외를 유지해야 한다")
    void recordsReadFailure() {
        var reader = mock(ChatReadUpdateService.class);
        var request = new ChatReadUpdateRequest(10L, 20L);
        var failure = new IllegalStateException("private-email@example.com");
        doThrow(failure).when(reader).update(null, request);
        var service = new ChatWebSocketService(mock(ChatMessageSendService.class), reader);

        try (var capture = new LogCapture(ChatWebSocketService.class)) {
            assertThatThrownBy(() -> service.publishReadUpdate(null, request)).isSameAs(failure);
            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getFormattedMessage())
                    .contains("10", "IllegalStateException").doesNotContain("private-email@example.com");
        }
    }
}
