package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomInfo;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            select new com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomInfo(
                r.id,
                p.id,
                p.title,
                p.thumbnailUrl,
                count(case when cm.id > coalesce(cr.lastReadMessageId, 0) then 1 else null end),
                max(cm.createdAt),
                r.createdAt
            )
            from ChatRoom r
            join r.pattern p
            join ChatMessage cm on cm.room = r
            left join ChatReadStatus cr on cr.room = r and cr.user.id = :adminUserId
            where p.deletedAt is null
            group by r.id, p.id, p.title, p.thumbnailUrl, r.createdAt
            having count(case when cm.id > coalesce(cr.lastReadMessageId, 0) then 1 else null end) > 0
            order by max(cm.createdAt) desc, r.id desc
            """,
            countQuery = """
            select count(distinct r.id)
            from ChatRoom r
            join r.pattern p
            join ChatMessage cm on cm.room = r
            left join ChatReadStatus cr on cr.room = r and cr.user.id = :adminUserId
            where p.deletedAt is null
              and cm.id > coalesce(cr.lastReadMessageId, 0)
            """)
    Page<AdminUnreadChatRoomInfo> findUnreadChatRoomsForAdmin(@Param("adminUserId") Long adminUserId, Pageable pageable);
}
