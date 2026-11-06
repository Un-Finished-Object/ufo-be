package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatEventOutboxDispatcher {

    private final ChatEventOutboxProcessor processor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatSocketEvent<?> event) {
        processRoom(event.roomId());
    }

    public void processRoom(Long roomId) {
        try {
            processor.processRoom(roomId);
        } catch (RuntimeException failure) {
            log.warn("채팅 이벤트 처리 실패: roomId={}\n{}",
                    roomId, ErrorLogSupport.stackTrace(failure));
        }
    }
}
