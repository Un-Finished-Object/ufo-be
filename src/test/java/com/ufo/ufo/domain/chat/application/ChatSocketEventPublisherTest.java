package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatSocketEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
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
}
