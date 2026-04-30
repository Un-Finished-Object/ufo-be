package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;

    @Transactional
    public void publishMessage(Principal principal, ChatMessageSendRequest request) {
        Long roomId = request.roomId();
        if (roomId == null) {
            return;
        }

        String clientMessageId = request.clientMessageId();
        Optional<User> maybeUser = resolveAuthenticatedUser(principal, roomId, clientMessageId);
        if (maybeUser.isEmpty()) {
            return;
        }

        Optional<ChatRoom> maybeRoom = chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId);
        if (maybeRoom.isEmpty()) {
            sendError(roomId, "CHAT_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다.", clientMessageId);
            return;
        }

        User sender = maybeUser.get();
        if (isNotJoinedRoom(sender.getId(), roomId)) {
            sendError(roomId, "CHAT_ROOM_FORBIDDEN", "접근 권한이 없는 채팅방입니다.", clientMessageId);
            return;
        }

        String text = normalizeText(request.text());
        if (text == null) {
            sendError(roomId, "INVALID_MESSAGE_TEXT", "메시지 내용은 비어 있을 수 없습니다.", clientMessageId);
            return;
        }

        Optional<ChatMessage> maybeReplyMessage = resolveReplyMessage(request, roomId, clientMessageId);
        if (request.replyRequested() && maybeReplyMessage.isEmpty()) {
            return;
        }

        ChatMessage savedMessage = chatMessageRepository.save(
                ChatMessage.builder()
                        .room(maybeRoom.get())
                        .user(sender)
                        .text(text)
                        .replyMessage(maybeReplyMessage.orElse(null))
                        .build()
        );

        ChatMessage replyMessage = savedMessage.getReplyMessage();
        ChatMessageCreatedPayload payload = new ChatMessageCreatedPayload(
                savedMessage.getId(),
                clientMessageId,
                sender.getId(),
                sender.getProfileImage(),
                sender.getNickname(),
                text,
                replyMessage == null ? null : replyMessage.getUser().getNickname(),
                replyMessage == null ? null : replyMessage.getId(),
                savedMessage.getCreatedAt()
        );
        messagingTemplate.convertAndSend(roomDestination(roomId),
                new ChatSocketEvent<>(ChatEventType.MESSAGE_CREATED, roomId, payload));
    }

    @Transactional
    public void publishReadUpdate(Principal principal, ChatReadUpdateRequest request) {
        Long roomId = request.roomId();
        if (roomId == null) {
            return;
        }

        Optional<User> maybeUser = resolveAuthenticatedUser(principal, roomId, null);
        if (maybeUser.isEmpty()) {
            return;
        }

        Optional<ChatRoom> maybeRoom = chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId);
        if (maybeRoom.isEmpty()) {
            sendError(roomId, "CHAT_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다.", null);
            return;
        }

        User user = maybeUser.get();
        if (isNotJoinedRoom(user.getId(), roomId)) {
            sendError(roomId, "CHAT_ROOM_FORBIDDEN", "접근 권한이 없는 채팅방입니다.", null);
            return;
        }

        Long lastReadMessageId = request.lastReadMessageId();
        if (lastReadMessageId == null || lastReadMessageId <= 0) {
            sendError(roomId, "INVALID_LAST_READ_MESSAGE_ID", "유효한 마지막 읽음 메시지 ID가 필요합니다.", null);
            return;
        }

        LocalDateTime readAt = LocalDateTime.now();

        ChatRoom room = maybeRoom.get();
        chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, user.getId())
                .ifPresentOrElse(
                        readStatus -> readStatus.update(lastReadMessageId, readAt),
                        () -> chatReadStatusRepository.save(ChatReadStatus.builder()
                                .room(room)
                                .user(user)
                                .lastReadMessageId(lastReadMessageId)
                                .readAt(readAt)
                                .build())
                );

        ChatReadUpdatedPayload payload = new ChatReadUpdatedPayload(
                user.getId(),
                lastReadMessageId,
                readAt
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

    private Optional<ChatMessage> resolveReplyMessage(ChatMessageSendRequest request, Long roomId, String clientMessageId) {
        if (!request.replyRequested()) {
            return Optional.empty();
        }

        Long replyMessageId = request.replyMessageId();
        if (replyMessageId == null || replyMessageId <= 0) {
            sendError(roomId, "CHAT_REPLY_MESSAGE_NOT_FOUND", "답장 대상 메시지를 찾을 수 없습니다.", clientMessageId);
            return Optional.empty();
        }

        Optional<ChatMessage> maybeReplyMessage = chatMessageRepository.findById(replyMessageId);
        if (maybeReplyMessage.isEmpty()) {
            sendError(roomId, "CHAT_REPLY_MESSAGE_NOT_FOUND", "답장 대상 메시지를 찾을 수 없습니다.", clientMessageId);
            return Optional.empty();
        }

        ChatMessage replyMessage = maybeReplyMessage.get();
        if (!replyMessage.getRoom().getId().equals(roomId)) {
            sendError(roomId, "CHAT_REPLY_MESSAGE_FORBIDDEN", "다른 채팅방의 메시지에는 답장할 수 없습니다.", clientMessageId);
            return Optional.empty();
        }
        return maybeReplyMessage;
    }

    private boolean isNotJoinedRoom(Long userId, Long roomId) {
        return chatRoomStatusRepository.findByUser_IdAndRoom_Id(userId, roomId).isEmpty();
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
