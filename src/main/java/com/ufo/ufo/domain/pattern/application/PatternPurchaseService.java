package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.exception.ChatRoomAlreadyPurchasedException;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.time.Duration;
import java.util.List;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternPurchaseService {
    @Value("${app.chat.segment-days}")
    private int chatSegmentDays;

    private final PatternRepository patternRepository;
    private final CreditService creditService;
    private final UserService userService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomStatusRepository chatRoomStatusRepository;

    @Transactional
    public PatternPurchaseResponse purchase(User user, Long patternId, PatternPurchaseRequest request) {
        Pattern pattern = findActivePattern(patternId);
        List<UnlockType> unlockTypes = request.toUnlockTypes();
        unlockTypes.forEach(unlockType -> creditService.purchaseUnlock(user, patternId, unlockType));
        if (unlockTypes.contains(UnlockType.CHAT)) {
            ensureChatRoomStatus(user, pattern);
        }
        return PatternPurchaseResponse.from(user.getId(), request.type());
    }

    public PatternPurchaseStatusResponse getStatus(User user, Long patternId) {
        findActivePattern(patternId);
        return PatternPurchaseStatusResponse.from(
                user.getId(),
                creditService.isUnlocked(user.getId(), patternId, UnlockType.CHAT),
                creditService.isUnlocked(user.getId(), patternId, UnlockType.YARN_INFO)
        );
    }

    private Pattern findActivePattern(Long patternId) {
        Pattern pattern = patternRepository.findById(patternId)
                .orElseThrow(PatternNotFoundException::new);
        if (pattern.getDeletedAt() != null) {
            throw new PatternNotFoundException();
        }
        return pattern;
    }

    private void ensureChatRoomStatus(User user, Pattern pattern) {
        User loginUser = userService.getUserById(user.getId());
        if (chatRoomStatusRepository.existsByUser_IdAndRoom_Pattern_Id(loginUser.getId(), pattern.getId())) {
            throw new ChatRoomAlreadyPurchasedException();
        }

        LocalDateTime joinedAt = LocalDateTime.now();
        ChatRoom chatRoom = resolveChatRoom(pattern, joinedAt);
        try {
            chatRoomStatusRepository.save(ChatRoomStatus.builder()
                    .user(loginUser)
                    .room(chatRoom)
                    .favorite(false)
                    .hidden(false)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new ChatRoomAlreadyPurchasedException();
        }
    }

    private ChatRoom resolveChatRoom(Pattern pattern, LocalDateTime joinedAt) {
        return chatRoomRepository.findFirstByPattern_IdAndSegmentStartAtLessThanEqualAndSegmentEndAtGreaterThan(
                pattern.getId(),
                joinedAt,
                joinedAt
        ).orElseGet(() -> createOrGetSegmentRoom(pattern, joinedAt));
    }

    private ChatRoom createSegmentRoom(Pattern pattern, LocalDateTime joinedAt) {
        LocalDateTime segmentStartAt = calculateSegmentStartAt(pattern, joinedAt);
        LocalDateTime segmentEndAt = segmentStartAt.plusDays(chatSegmentDays);

        return chatRoomRepository.save(ChatRoom.builder()
                .pattern(pattern)
                .segmentStartAt(segmentStartAt)
                .segmentEndAt(segmentEndAt)
                .build());
    }

    private ChatRoom createOrGetSegmentRoom(Pattern pattern, LocalDateTime joinedAt) {
        LocalDateTime segmentStartAt = calculateSegmentStartAt(pattern, joinedAt);
        try {
            return createSegmentRoom(pattern, joinedAt);
        } catch (DataIntegrityViolationException exception) {
            return chatRoomRepository.findByPattern_IdAndSegmentStartAt(pattern.getId(), segmentStartAt)
                    .orElseThrow(() -> exception);
        }
    }

    private LocalDateTime calculateSegmentStartAt(Pattern pattern, LocalDateTime joinedAt) {
        LocalDateTime baseAt = pattern.getCreatedAt() != null ? pattern.getCreatedAt() : joinedAt;
        long days = Math.max(0, Duration.between(baseAt, joinedAt).toDays());
        int segmentNo = Math.toIntExact(days / chatSegmentDays);
        return baseAt.plusDays((long) segmentNo * chatSegmentDays);
    }
}
