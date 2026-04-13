package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoom_IdOrderByIdDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoom_IdAndIdLessThanOrderByIdDesc(Long roomId, Long messageId, Pageable pageable);

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
