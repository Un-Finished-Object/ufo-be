package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByPattern_IdOrderByIdDesc(Long patternId, Pageable pageable);

    List<ChatMessage> findByPattern_IdAndIdLessThanOrderByIdDesc(Long patternId, Long messageId, Pageable pageable);

    @Query("""
            select new com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount(cm.pattern.id, count(cm))
            from ChatMessage cm
            left join ChatReadStatus cr
              on cr.pattern = cm.pattern and cr.user.id = :userId
            where cm.pattern.id in :patternIds
              and cm.id > coalesce(cr.lastReadMessageId, 0)
            group by cm.pattern.id
            """)
    List<ChatUnreadCount> countUnreadByPatternIds(
            @Param("userId") Long userId,
            @Param("patternIds") List<Long> patternIds
    );
}
