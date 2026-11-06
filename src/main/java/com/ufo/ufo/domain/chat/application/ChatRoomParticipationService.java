package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.pattern.exception.ChatRoomAlreadyPurchasedException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomParticipationService {

    private final UserService userService;
    private final ChatRoomProvisioningService provisioningService;
    private final ChatRoomStatusRepository statusRepository;
    private final ChatNicknameGenerator nicknameGenerator;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void join(User user, Pattern pattern) {
        User participant = userService.getUserByIdForUpdate(user.getId());
        if (statusRepository.existsByUser_IdAndRoom_Pattern_Id(participant.getId(), pattern.getId())) {
            throw new ChatRoomAlreadyPurchasedException();
        }

        ChatRoom assignedRoom = provisioningService.assignJoinableRoom(pattern);
        ChatRoom room = provisioningService.lockRoom(assignedRoom);
        String nickname = nicknameGenerator.generate(statusRepository.countByRoom_Id(room.getId()));
        try {
            statusRepository.saveAndFlush(ChatRoomStatus.builder()
                    .user(participant)
                    .room(room)
                    .favorite(false)
                    .hidden(false)
                    .nickname(nickname)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new ChatRoomAlreadyPurchasedException();
        }
    }

    public Optional<Long> findLatestJoinedRoomId(Long userId, Long patternId) {
        return statusRepository.findFirstByUser_IdAndRoom_Pattern_IdOrderByCreatedAtDescIdDesc(userId, patternId)
                .map(status -> status.getRoom().getId());
    }
}
