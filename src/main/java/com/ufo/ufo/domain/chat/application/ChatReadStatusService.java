package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.exception.UserNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadStatusService {

    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ChatReadStatus> markRead(User user, ChatRoom room, Long messageId) {
        if (messageId == null || messageId <= 0
                || chatMessageRepository.findByIdAndRoom_Id(messageId, room.getId()).isEmpty()) {
            return Optional.empty();
        }

        // 첫 읽음 요청의 중복 저장을 막도록 사용자 행을 먼저 잠근다.
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(UserNotFoundException::new);
        LocalDateTime readAt = LocalDateTime.now();
        ChatReadStatus status = chatReadStatusRepository.findByRoom_IdAndUser_Id(room.getId(), lockedUser.getId())
                .orElseGet(() -> chatReadStatusRepository.save(ChatReadStatus.builder()
                        .room(room)
                        .user(lockedUser)
                        .lastReadMessageId(messageId)
                        .readAt(readAt)
                        .build()));
        status.update(messageId, readAt);
        return Optional.of(status);
    }
}
