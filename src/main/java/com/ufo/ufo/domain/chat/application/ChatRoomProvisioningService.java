package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.exception.ChatRoomNotFoundException;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomProvisioningService {

    @Value("${app.chat.segment-days}")
    private int chatSegmentDays;

    private final ChatRoomRepository chatRoomRepository;
    private final PatternRepository patternRepository;

    @Transactional
    public ChatRoom assignJoinableRoom(Pattern pattern) {
        Pattern lockedPattern = patternRepository.findByIdAndDeletedAtIsNull(pattern.getId())
                .orElseThrow(PatternNotFoundException::new);
        LocalDateTime anchorAt = LocalDateTime.now();

        return chatRoomRepository.findFirstByPattern_IdAndSegmentStartAtLessThanEqualAndSegmentEndAtGreaterThan(
                lockedPattern.getId(),
                anchorAt,
                anchorAt
        ).orElseGet(() -> createOrGetSegmentRoom(lockedPattern, anchorAt));
    }

    @Transactional
    public ChatRoom createOrGetSegmentRoom(Pattern pattern, LocalDateTime segmentStartAt) {
        LocalDateTime segmentEndAt = segmentStartAt.plusDays(chatSegmentDays);
        try {
            return chatRoomRepository.save(ChatRoom.builder()
                    .pattern(pattern)
                    .segmentStartAt(segmentStartAt)
                    .segmentEndAt(segmentEndAt)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            return chatRoomRepository.findByPattern_IdAndSegmentStartAt(pattern.getId(), segmentStartAt)
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional
    public ChatRoom lockRoom(ChatRoom room) {
        return chatRoomRepository.findByIdForUpdate(room.getId())
                .orElseThrow(ChatRoomNotFoundException::new);
    }

}
