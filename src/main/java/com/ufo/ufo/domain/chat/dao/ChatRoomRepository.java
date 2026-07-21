package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatRoom;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByIdAndPattern_DeletedAtIsNull(Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cr from ChatRoom cr where cr.id = :roomId")
    Optional<ChatRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    boolean existsByIdAndPattern_DeletedAtIsNull(Long roomId);

    Optional<ChatRoom> findFirstByPattern_IdAndSegmentStartAtLessThanEqualAndSegmentEndAtGreaterThan(
            Long patternId,
            LocalDateTime at,
            LocalDateTime sameAt
    );

    Optional<ChatRoom> findByPattern_IdAndSegmentStartAt(Long patternId, LocalDateTime segmentStartAt);
}
