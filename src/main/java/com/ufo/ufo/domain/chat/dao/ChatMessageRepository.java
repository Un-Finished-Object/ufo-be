package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomLastMessage;
import com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            select cm
            from ChatMessage cm
            join fetch cm.user
            left join fetch cm.replyMessage rm
            left join fetch rm.user
            where cm.room.id = :roomId
            order by cm.id desc
            """)
    List<ChatMessage> findByRoom_IdOrderByIdDesc(@Param("roomId") Long roomId, Pageable pageable);

    @Query("""
            select cm
            from ChatMessage cm
            join fetch cm.user
            left join fetch cm.replyMessage rm
            left join fetch rm.user
            where cm.room.id = :roomId
              and cm.id < :messageId
            order by cm.id desc
            """)
    List<ChatMessage> findByRoom_IdAndIdLessThanOrderByIdDesc(
            @Param("roomId") Long roomId,
            @Param("messageId") Long messageId,
            Pageable pageable
    );

    Optional<ChatMessage> findByIdAndRoom_Id(Long id, Long roomId);

    @Query("""
            select new com.ufo.ufo.domain.chat.dto.response.ChatRoomLastMessage(cm.room.id, cm.text)
            from ChatMessage cm
            where cm.room.id in :roomIds
              and cm.id = (
                  select max(cm2.id)
                  from ChatMessage cm2
                  where cm2.room = cm.room
              )
            """)
    List<ChatRoomLastMessage> findLatestMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);

    @Query("""
            select new com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount(cm.room.id, count(cm))
            from ChatMessage cm
            left join ChatReadStatus cr
              on cr.room = cm.room and cr.user.id = :userId
            where cm.room.id in :roomIds
              and cm.id > coalesce(cr.lastReadMessageId, 0)
            group by cm.room.id
            """)
    List<ChatUnreadCount> countUnreadByRoomIds(
            @Param("userId") Long userId,
            @Param("roomIds") List<Long> roomIds
    );
}
