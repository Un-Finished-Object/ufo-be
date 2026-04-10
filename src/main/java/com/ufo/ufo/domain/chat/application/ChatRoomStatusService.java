package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.request.UpdateChatRoomStatusRequest;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomStatusResponse;
import com.ufo.ufo.domain.chat.exception.ChatRoomForbiddenException;
import com.ufo.ufo.domain.chat.exception.ChatRoomNotFoundException;
import com.ufo.ufo.domain.chat.exception.InvalidChatRoomStatusUpdateException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomStatusService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final UserService userService;

    @Transactional
    public ChatRoomStatusResponse updateStatus(User user, Long roomId, UpdateChatRoomStatusRequest request) {
        if (request == null || !request.hasUpdates()) {
            throw new InvalidChatRoomStatusUpdateException();
        }

        validateActiveRoom(roomId);

        User loginUser = userService.getUserById(user.getId());
        ChatRoomStatus status = chatRoomStatusRepository.findByUser_IdAndRoom_Id(loginUser.getId(), roomId)
                .orElseThrow(ChatRoomForbiddenException::new);
        status.update(request.favorite(), request.hidden());

        return ChatRoomStatusResponse.of(roomId, status.isFavorite(), status.isHidden());
    }

    private void validateActiveRoom(Long roomId) {
        if (!chatRoomRepository.existsByIdAndPattern_DeletedAtIsNull(roomId)) {
            throw new ChatRoomNotFoundException();
        }
    }
}
