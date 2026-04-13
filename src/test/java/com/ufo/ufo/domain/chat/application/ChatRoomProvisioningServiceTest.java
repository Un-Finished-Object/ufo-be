package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅방 프로비저닝 서비스 테스트")
class ChatRoomProvisioningServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private PatternRepository patternRepository;

    @InjectMocks
    private ChatRoomProvisioningService chatRoomProvisioningService;

    @Test
    @DisplayName("현재 구간 방이 있으면 해당 방을 반환해야 한다")
    void assignJoinableRoom_WhenCurrentRoomExists_ReturnsCurrentRoom() {
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        ChatRoom currentRoom = ChatRoomFixture.createRoomWithId(pattern, 100L);
        LocalDateTime joinedAt = LocalDateTime.of(2026, 4, 13, 10, 0);

        when(patternRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(pattern));
        when(chatRoomRepository.findFirstByPattern_IdAndSegmentStartAtLessThanEqualAndSegmentEndAtGreaterThan(
                10L, joinedAt, joinedAt
        )).thenReturn(Optional.of(currentRoom));

        ChatRoom result = chatRoomProvisioningService.assignJoinableRoom(pattern, joinedAt);

        assertThat(result).isSameAs(currentRoom);
        verify(chatRoomRepository)
                .findFirstByPattern_IdAndSegmentStartAtLessThanEqualAndSegmentEndAtGreaterThan(10L, joinedAt, joinedAt);
    }

    @Test
    @DisplayName("세그먼트 방 생성 충돌 시 시작 시각 기준으로 기존 방을 재조회해야 한다")
    void createOrGetSegmentRoom_WhenDuplicate_UsesExistingRoom() {
        Pattern pattern = PatternFixture.createPatternWithId(10L);
        LocalDateTime targetAt = LocalDateTime.of(2026, 4, 13, 10, 0);
      ChatRoom existingRoom = ChatRoomFixture.createRoomWithId(pattern, 200L);

        when(chatRoomRepository.save(any(ChatRoom.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate room"));
        when(chatRoomRepository.findByPattern_IdAndSegmentStartAt(10L, targetAt))
                .thenReturn(Optional.of(existingRoom));

        ChatRoom result = chatRoomProvisioningService.createOrGetSegmentRoom(pattern, targetAt);

        assertThat(result).isSameAs(existingRoom);
        verify(chatRoomRepository).findByPattern_IdAndSegmentStartAt(eq(10L), eq(targetAt));
    }
}
