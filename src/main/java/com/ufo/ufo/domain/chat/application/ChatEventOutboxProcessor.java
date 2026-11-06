package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatEventOutboxRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.domain.ChatEventOutbox;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatEventOutboxProcessor {

    private final ChatRoomRepository roomRepository;
    private final ChatEventOutboxRepository repository;
    private final ChatEventCodec codec;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void processRoom(Long roomId) {
        if (roomRepository.findByIdForUpdate(roomId).isEmpty()) {
            return;
        }
        for (int count = 0; count < 50; count++) {
            ChatEventOutbox entry = repository.findFirstByRoomIdAndPublishedAtIsNullOrderByIdAsc(roomId)
                    .orElse(null);
            if (entry == null || entry.getNextAttemptAt().isAfter(LocalDateTime.now())) {
                return;
            }
            entry.startAttempt();
            try {
                messagingTemplate.convertAndSend("/sub/chat/rooms/" + roomId, codec.decode(entry));
                entry.complete(LocalDateTime.now());
            } catch (RuntimeException failure) {
                entry.defer(LocalDateTime.now());
                log.warn("채팅 이벤트 전송 실패: eventId={}, roomId={}, eventType={}, attempts={}\n{}",
                        entry.getId(), roomId, entry.getEventType(), entry.getAttempts(),
                        ErrorLogSupport.stackTrace(failure));
                return;
            }
            repository.flush();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deletePublished() {
        repository.deletePublishedBefore(LocalDateTime.now().minusDays(7));
    }
}
