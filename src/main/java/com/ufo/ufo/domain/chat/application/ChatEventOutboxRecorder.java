package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatEventOutboxRepository;
import com.ufo.ufo.domain.chat.domain.ChatEventOutbox;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatEventOutboxRecorder {

    private final ChatEventOutboxRepository repository;
    private final ChatEventCodec codec;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(ChatSocketEvent<?> event) {
        repository.save(new ChatEventOutbox(event.roomId(), event.eventType(), codec.encode(event)));
    }
}
