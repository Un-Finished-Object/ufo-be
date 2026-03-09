package com.ufo.ufo.domain.chat.dao;

import com.ufo.ufo.domain.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByPattern_IdOrderByIdDesc(Long patternId, Pageable pageable);

    List<ChatMessage> findByPattern_IdAndIdLessThanOrderByIdDesc(Long patternId, Long messageId, Pageable pageable);
}
