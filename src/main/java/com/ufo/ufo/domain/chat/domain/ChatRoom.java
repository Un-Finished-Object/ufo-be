package com.ufo.ufo.domain.chat.domain;

import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_room_pattern_segment_start", columnNames = {"pattern_id", "segment_start_at"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pattern_id")
    private Pattern pattern;

    @Column(name = "segment_start_at", nullable = false)
    private LocalDateTime segmentStartAt;

    @Column(name = "segment_end_at", nullable = false)
    private LocalDateTime segmentEndAt;

    @Builder
    public ChatRoom(Pattern pattern, LocalDateTime segmentStartAt, LocalDateTime segmentEndAt) {
        this.pattern = pattern;
        this.segmentStartAt = segmentStartAt;
        this.segmentEndAt = segmentEndAt;
    }
}
