package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.exception.ChatNicknameNotFoundException;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import java.security.Principal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSocketAccessService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final UserRepository userRepository;
    private final ChatSocketEventPublisher eventPublisher;

    Optional<User> resolveUser(Principal principal, Long roomId, String clientMessageId) {
        String userEmail = extractUserEmail(principal);
        if (userEmail == null) {
            eventPublisher.sendError(roomId, "UNAUTHORIZED", "인증 사용자 정보를 확인할 수 없습니다.", clientMessageId);
            return Optional.empty();
        }

        Optional<User> user = userRepository.findByEmail(userEmail);
        if (user.isEmpty()) {
            eventPublisher.sendError(roomId, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다.", clientMessageId);
        }
        return user;
    }

    Optional<ChatRoom> resolveRoom(Long roomId, String clientMessageId) {
        Optional<ChatRoom> room = chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId);
        if (room.isEmpty()) {
            eventPublisher.sendError(roomId, "CHAT_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다.", clientMessageId);
        }
        return room;
    }

    Optional<ChatRoomStatus> resolveRoomStatus(Long userId, Long roomId, String clientMessageId) {
        Optional<ChatRoomStatus> status = chatRoomStatusRepository.findByUser_IdAndRoom_Id(userId, roomId);
        if (status.isEmpty()) {
            eventPublisher.sendError(roomId, "CHAT_ROOM_FORBIDDEN", "접근 권한이 없는 채팅방입니다.", clientMessageId);
        }
        return status;
    }

    String getChatNickname(Long userId, Long roomId) {
        return chatRoomStatusRepository.findByUser_IdAndRoom_Id(userId, roomId)
                .map(ChatRoomStatus::getNickname)
                .filter(nickname -> !nickname.isBlank())
                .orElseThrow(ChatNicknameNotFoundException::new);
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
}
