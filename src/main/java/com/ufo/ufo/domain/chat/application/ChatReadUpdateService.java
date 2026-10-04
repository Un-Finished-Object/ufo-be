package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.user.domain.User;
import java.security.Principal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadUpdateService {

    private final ChatSocketAccessService accessService;
    private final ChatReadStatusService readStatusService;
    private final ChatSocketEventPublisher eventPublisher;

    @Transactional(isolation = Isolation.READ_COMMITTED)
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

        Optional<ChatReadStatus> savedStatus = readStatusService.markRead(user, maybeRoom.get(), lastReadMessageId);
        if (savedStatus.isEmpty()) {
            eventPublisher.sendError(roomId, "INVALID_LAST_READ_MESSAGE_ID", "해당 채팅방의 메시지가 아닙니다.", null);
            return;
        }
        ChatReadStatus status = savedStatus.get();
        ChatReadUpdatedPayload payload = new ChatReadUpdatedPayload(
                user.getId(), status.getLastReadMessageId(), status.getReadAt());
        eventPublisher.sendReadUpdated(roomId, payload);
    }
}
