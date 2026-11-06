package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatEventOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class ChatEventOutboxTaskRunner {

    private final ChatEventOutboxRepository repository;
    private final ChatEventOutboxDispatcher dispatcher;
    private final ChatEventOutboxProcessor processor;

    @Scheduled(fixedDelayString = "${app.chat.outbox.delay-ms:1000}",
            initialDelayString = "${app.chat.outbox.delay-ms:1000}")
    public void retryPending() {
        repository.findDueRoomIds(LocalDateTime.now(), PageRequest.of(0, 50)).forEach(dispatcher::processRoom);
    }

    @Scheduled(fixedDelayString = "${app.chat.outbox.cleanup-delay-ms:3600000}")
    public void deletePublished() {
        processor.deletePublished();
    }
}
