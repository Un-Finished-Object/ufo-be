package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;

    void sendMessageCreated(Long roomId, ChatMessageCreatedPayload payload) {
        applicationEventPublisher.publishEvent(new ChatSocketEvent<>(ChatEventType.MESSAGE_CREATED, roomId, payload));
    }

    void sendReadUpdated(Long roomId, ChatReadUpdatedPayload payload) {
        applicationEventPublisher.publishEvent(new ChatSocketEvent<>(ChatEventType.READ_UPDATED, roomId, payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendCommittedEvent(ChatSocketEvent<?> event) {
        try {
            messagingTemplate.convertAndSend(roomDestination(event.roomId()), event);
        } catch (RuntimeException exception) {
            String clientMessageId = event.payload() instanceof ChatMessageCreatedPayload payload
                    ? payload.clientMessageId() : null;
            log.error("채팅 성공 이벤트 전송 실패: roomId={}, eventType={}, clientMessageId={}\n{}",
                    event.roomId(), event.eventType(), ErrorLogSupport.identifier(clientMessageId),
                    ErrorLogSupport.stackTrace(exception));
        }
    }

    void sendError(Long roomId, String code, String message, String clientMessageId) {
        log.warn("채팅 요청 거부: roomId={}, code={}, clientMessageId={}", roomId,
                ErrorLogSupport.identifier(code), ErrorLogSupport.identifier(clientMessageId));
        ChatErrorPayload payload = new ChatErrorPayload(code, message, clientMessageId);
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.ERROR, roomId, payload));
    }

    private String roomDestination(Long roomId) {
        return "/sub/chat/rooms/" + roomId;
    }
}
