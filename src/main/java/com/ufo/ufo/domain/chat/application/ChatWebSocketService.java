package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final PatternRepository patternRepository;
    private final UserRepository userRepository;

    private final AtomicLong messageSequence = new AtomicLong(0);

    public void publishMessage(Principal principal, ChatMessageSendRequest request) {
        Long roomId = request.roomId();
        if (roomId == null) {
            return;
        }

        String clientMessageId = request.clientMessageId();
        if (!patternRepository.existsById(roomId)) {
            sendError(roomId, "CHAT_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다.", clientMessageId);
            return;
        }

        Optional<User> maybeUser = resolveAuthenticatedUser(principal, roomId, clientMessageId);
        if (maybeUser.isEmpty()) {
            return;
        }

        String text = normalizeText(request.text());
        if (text == null) {
            sendError(roomId, "INVALID_MESSAGE_TEXT", "메시지 내용은 비어 있을 수 없습니다.", clientMessageId);
            return;
        }

        User sender = maybeUser.get();
        ChatMessageCreatedPayload payload = new ChatMessageCreatedPayload(
                messageSequence.incrementAndGet(),
                clientMessageId,
                sender.getId(),
                sender.getNickname(),
                text,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.MESSAGE_CREATED, roomId, payload));
    }

    public void publishReadUpdate(Principal principal, ChatReadUpdateRequest request) {
        Long roomId = request.roomId();
        if (roomId == null) {
            return;
        }
        if (!patternRepository.existsById(roomId)) {
            sendError(roomId, "CHAT_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다.", null);
            return;
        }

        Optional<User> maybeUser = resolveAuthenticatedUser(principal, roomId, null);
        if (maybeUser.isEmpty()) {
            return;
        }

        Long lastReadMessageId = request.lastReadMessageId();
        if (lastReadMessageId == null || lastReadMessageId <= 0) {
            sendError(roomId, "INVALID_LAST_READ_MESSAGE_ID", "유효한 마지막 읽음 메시지 ID가 필요합니다.", null);
            return;
        }

        User user = maybeUser.get();
        ChatReadUpdatedPayload payload = new ChatReadUpdatedPayload(
                user.getId(),
                lastReadMessageId,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.READ_UPDATED, roomId, payload));
    }

    private Optional<User> resolveAuthenticatedUser(Principal principal, Long roomId, String clientMessageId) {
        String userEmail = extractUserEmail(principal);
        if (userEmail == null) {
            sendError(roomId, "UNAUTHORIZED", "인증 사용자 정보를 확인할 수 없습니다.", clientMessageId);
            return Optional.empty();
        }

        Optional<User> maybeUser = userRepository.findByEmail(userEmail);
        if (maybeUser.isEmpty()) {
            sendError(roomId, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다.", clientMessageId);
            return Optional.empty();
        }
        return maybeUser;
    }

    private String extractUserEmail(Principal principal) {
        if (principal == null) {
            return null;
        }
        String name = principal.getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return name;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed;
    }

    private void sendError(Long roomId, String code, String message, String clientMessageId) {
        ChatErrorPayload payload = new ChatErrorPayload(code, message, clientMessageId);
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.ERROR, roomId, payload));
    }

    private String roomDestination(Long roomId) {
        return "/sub/chat/rooms/" + roomId;
    }
}
