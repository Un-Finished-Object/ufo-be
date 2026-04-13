package com.ufo.ufo.domain.chat.domain;

import com.ufo.ufo.domain.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "chat_read_statuses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_read_status_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public ChatReadStatus(ChatRoom room, User user, Long lastReadMessageId, LocalDateTime readAt) {
        this.room = room;
        this.user = user;
        this.lastReadMessageId = lastReadMessageId;
        this.readAt = readAt;
    }

    public void update(Long lastReadMessageId, LocalDateTime readAt) {
        this.lastReadMessageId = lastReadMessageId;
        this.readAt = readAt;
    }
}
