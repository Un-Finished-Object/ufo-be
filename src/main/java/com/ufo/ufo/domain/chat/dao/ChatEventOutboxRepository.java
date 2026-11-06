package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatEventOutboxRepository extends JpaRepository<ChatEventOutbox, Long> {

    Optional<ChatEventOutbox> findFirstByRoomIdAndPublishedAtIsNullOrderByIdAsc(Long roomId);

    @Query("""
            select e.roomId from ChatEventOutbox e
            where e.publishedAt is null and e.nextAttemptAt <= :now
              and not exists (
                  select older.id from ChatEventOutbox older
                  where older.roomId = e.roomId and older.publishedAt is null and older.id < e.id
              )
            order by e.id
            """)
    List<Long> findDueRoomIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("delete from ChatEventOutbox e where e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") LocalDateTime before);
}
