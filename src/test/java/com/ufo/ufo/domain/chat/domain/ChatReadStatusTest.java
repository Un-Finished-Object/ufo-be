package com.ufo.ufo.domain.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatReadStatusTest {

    private final LocalDateTime originalReadAt = LocalDateTime.of(2026, 10, 4, 12, 0);

    @Test
    @DisplayName("이전 메시지의 읽음 요청은 읽음 위치와 시각을 바꾸지 않는다")
    void olderMessageDoesNotMoveReadPositionBackwards() {
        ChatReadStatus status = ChatReadStatus.builder()
                .lastReadMessageId(20L).readAt(originalReadAt).build();

        status.update(10L, originalReadAt.plusMinutes(1));

        assertThat(status.getLastReadMessageId()).isEqualTo(20L);
        assertThat(status.getReadAt()).isEqualTo(originalReadAt);
    }

    @Test
    @DisplayName("같은 메시지의 중복 읽음 요청은 최초 읽음 시각을 유지한다")
    void duplicateMessagePreservesReadAt() {
        ChatReadStatus status = ChatReadStatus.builder()
                .lastReadMessageId(20L).readAt(originalReadAt).build();

        status.update(20L, originalReadAt.plusMinutes(1));

        assertThat(status.getReadAt()).isEqualTo(originalReadAt);
    }

    @Test
    @DisplayName("새 메시지를 읽으면 읽음 위치와 시각을 함께 갱신한다")
    void newerMessageAdvancesReadPosition() {
        ChatReadStatus status = ChatReadStatus.builder()
                .lastReadMessageId(20L).readAt(originalReadAt).build();

        status.update(30L, originalReadAt.plusMinutes(1));

        assertThat(status.getLastReadMessageId()).isEqualTo(30L);
        assertThat(status.getReadAt()).isEqualTo(originalReadAt.plusMinutes(1));
    }
}
