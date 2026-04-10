package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatRoom;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByIdAndPattern_DeletedAtIsNull(Long roomId);

    boolean existsByIdAndPattern_DeletedAtIsNull(Long roomId);

    Optional<ChatRoom> findFirstByPattern_IdAndSegmentStartAtLessThanEqualAndSegmentEndAtGreaterThan(
            Long patternId,
            LocalDateTime at,
            LocalDateTime sameAt
    );

    Optional<ChatRoom> findByPattern_IdAndSegmentNo(Long patternId, Integer segmentNo);
}
