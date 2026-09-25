package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    void sendMessageCreated(Long roomId, ChatMessageCreatedPayload payload) {
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.MESSAGE_CREATED, roomId, payload));
    }

    void sendReadUpdated(Long roomId, ChatReadUpdatedPayload payload) {
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.READ_UPDATED, roomId, payload));
    }

    void sendError(Long roomId, String code, String message, String clientMessageId) {
        ChatErrorPayload payload = new ChatErrorPayload(code, message, clientMessageId);
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.ERROR, roomId, payload));
    }

    private String roomDestination(Long roomId) {
        return "/sub/chat/rooms/" + roomId;
    }
}
