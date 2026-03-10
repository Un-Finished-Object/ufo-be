package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomStatusRepository extends JpaRepository<ChatRoomStatus, Long> {
    Optional<ChatRoomStatus> findByUser_IdAndPattern_Id(Long userId, Long patternId);
}
