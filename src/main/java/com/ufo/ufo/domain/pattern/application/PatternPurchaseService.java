package com.ufo.ufo.domain.pattern.application;

import com.ufo.ufo.domain.chat.application.ChatNicknameGenerator;
import com.ufo.ufo.domain.chat.application.ChatRoomProvisioningService;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.credit.application.CreditService;
import com.ufo.ufo.domain.credit.domain.UnlockType;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.dto.request.PatternPurchaseRequest;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseResponse;
import com.ufo.ufo.domain.pattern.dto.response.PatternPurchaseStatusResponse;
import com.ufo.ufo.domain.pattern.exception.ChatRoomAlreadyPurchasedException;
import com.ufo.ufo.domain.pattern.exception.PatternNotFoundException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternPurchaseService {

    private final PatternRepository patternRepository;
    private final CreditService creditService;
    private final UserService userService;
    private final ChatRoomProvisioningService chatRoomProvisioningService;
    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final ChatNicknameGenerator chatNicknameGenerator;

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
        Long chatRoomId = chatRoomStatusRepository
                .findFirstByUser_IdAndRoom_Pattern_IdOrderByCreatedAtDescIdDesc(user.getId(), patternId)
                .map(chatRoomStatus -> chatRoomStatus.getRoom().getId())
                .orElse(null);
        return PatternPurchaseStatusResponse.from(
                user.getId(),
                creditService.isUnlocked(user.getId(), patternId, UnlockType.CHAT),
                creditService.isUnlocked(user.getId(), patternId, UnlockType.YARN_INFO),
                chatRoomId
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

        ChatRoom assignedRoom = chatRoomProvisioningService.assignJoinableRoom(pattern);
        ChatRoom chatRoom = chatRoomProvisioningService.lockRoom(assignedRoom);
        long roomStatusCount = chatRoomStatusRepository.countByRoom_Id(chatRoom.getId());
        String chatNickname = chatNicknameGenerator.generate(roomStatusCount);
        try {
            chatRoomStatusRepository.save(ChatRoomStatus.builder()
                    .user(loginUser)
                    .room(chatRoom)
                    .favorite(false)
                    .hidden(false)
                    .nickname(chatNickname)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new ChatRoomAlreadyPurchasedException();
        }
    }
}
