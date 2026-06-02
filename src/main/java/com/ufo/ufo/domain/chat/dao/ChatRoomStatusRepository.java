package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomUserCount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomStatusRepository extends JpaRepository<ChatRoomStatus, Long> {
    Optional<ChatRoomStatus> findByUser_IdAndRoom_Id(Long userId, Long roomId);

    @EntityGraph(attributePaths = {"room"})
    Optional<ChatRoomStatus> findFirstByUser_IdAndRoom_Pattern_IdOrderByCreatedAtDescIdDesc(Long userId, Long patternId);

    boolean existsByUser_IdAndRoom_Pattern_Id(Long userId, Long patternId);

    @EntityGraph(attributePaths = {"room", "room.pattern"})
    List<ChatRoomStatus> findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(Long userId);

    @Query("""
            select new com.ufo.ufo.domain.chat.dto.response.ChatRoomUserCount(crs.room.id, count(crs))
            from ChatRoomStatus crs
            where crs.room.id in :roomIds
              and crs.hidden = false
            group by crs.room.id
            """)
    List<ChatRoomUserCount> countUfoUsersByRoomIds(@Param("roomIds") List<Long> roomIds);
}
