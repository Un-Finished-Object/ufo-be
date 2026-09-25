package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.user.domain.User;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadUpdateService {

    private final ChatSocketAccessService accessService;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final ChatSocketEventPublisher eventPublisher;

    @Transactional
    public void update(Principal principal, ChatReadUpdateRequest request) {
        Long roomId = request.roomId();
        if (roomId == null) {
            return;
        }

        Optional<User> maybeUser = accessService.resolveUser(principal, roomId, null);
        if (maybeUser.isEmpty()) {
            return;
        }

        Optional<ChatRoom> maybeRoom = accessService.resolveRoom(roomId, null);
        if (maybeRoom.isEmpty()) {
            return;
        }

        User user = maybeUser.get();
        if (accessService.resolveRoomStatus(user.getId(), roomId, null).isEmpty()) {
            return;
        }

        Long lastReadMessageId = request.lastReadMessageId();
        if (lastReadMessageId == null || lastReadMessageId <= 0) {
            eventPublisher.sendError(roomId, "INVALID_LAST_READ_MESSAGE_ID", "유효한 마지막 읽음 메시지 ID가 필요합니다.", null);
            return;
        }

        LocalDateTime readAt = LocalDateTime.now();
        chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, user.getId())
                .ifPresentOrElse(
                        readStatus -> readStatus.update(lastReadMessageId, readAt),
                        () -> chatReadStatusRepository.save(ChatReadStatus.builder()
                                .room(maybeRoom.get())
                                .user(user)
                                .lastReadMessageId(lastReadMessageId)
                                .readAt(readAt)
                                .build())
                );

        ChatReadUpdatedPayload payload = new ChatReadUpdatedPayload(user.getId(), lastReadMessageId, readAt);
        eventPublisher.sendReadUpdated(roomId, payload);
    }
}
