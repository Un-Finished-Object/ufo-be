package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomStatusRepository extends JpaRepository<ChatRoomStatus, Long> {
    Optional<ChatRoomStatus> findByUser_IdAndPattern_Id(Long userId, Long patternId);

    @Query("""
            select crs from ChatRoomStatus crs
            join fetch crs.pattern p
            where crs.user.id = :userId
              and p.deletedAt is null
            order by crs.createdAt desc, crs.id desc
            """)
    List<ChatRoomStatus> findAllActiveByUserIdOrderByLatest(@Param("userId") Long userId);
}
