package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ChatEventOutboxDispatcher {

    private final ChatEventOutboxProcessor processor;
    private final Executor executor;

    public ChatEventOutboxDispatcher(ChatEventOutboxProcessor processor,
                                    @Qualifier("chatEventOutboxExecutor") Executor executor) {
        this.processor = processor;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatSocketEvent<?> event) {
        try {
            executor.execute(() -> processRoom(event.roomId()));
        } catch (RejectedExecutionException failure) {
            log.warn("채팅 이벤트 즉시 처리 예약 실패: roomId={}\n{}",
                    event.roomId(), ErrorLogSupport.stackTrace(failure));
        }
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
