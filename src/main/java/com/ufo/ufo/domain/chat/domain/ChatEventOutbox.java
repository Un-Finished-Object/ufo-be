package com.ufo.ufo.domain.chat.domain;

import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_event_outbox", indexes = {
        @Index(name = "idx_chat_outbox_room_pending", columnList = "room_id,published_at,event_id"),
        @Index(name = "idx_chat_outbox_due", columnList = "published_at,next_attempt_at,event_id")
})
public class ChatEventOutbox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChatEventType eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String eventJson;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    private LocalDateTime publishedAt;

    public ChatEventOutbox(Long roomId, ChatEventType eventType, String eventJson) {
        this.roomId = roomId;
        this.eventType = eventType;
        this.eventJson = eventJson;
        this.nextAttemptAt = LocalDateTime.now();
    }

    public void startAttempt() {
        if (attempts < Integer.MAX_VALUE) {
            attempts++;
        }
    }

    public void complete(LocalDateTime now) {
        publishedAt = now;
    }

    public void defer(LocalDateTime now) {
        long delaySeconds = Math.min(300, 30L << Math.min(Math.max(attempts - 1, 0), 4));
        nextAttemptAt = now.plusSeconds(delaySeconds);
    }
}
