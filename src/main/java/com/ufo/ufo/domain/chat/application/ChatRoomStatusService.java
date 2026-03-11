package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.request.UpdateChatRoomStatusRequest;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomStatusResponse;
import com.ufo.ufo.domain.chat.exception.ChatRoomNotFoundException;
import com.ufo.ufo.domain.chat.exception.InvalidChatRoomStatusUpdateException;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomStatusService {

    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final PatternRepository patternRepository;
    private final UserService userService;

    @Transactional
    public ChatRoomStatusResponse updateStatus(User user, Long patternId, UpdateChatRoomStatusRequest request) {
        if (request == null || !request.hasUpdates()) {
            throw new InvalidChatRoomStatusUpdateException();
        }

        Pattern pattern = patternRepository.findById(patternId)
                .orElseThrow(ChatRoomNotFoundException::new);
        if (pattern.getDeletedAt() != null) {
            throw new ChatRoomNotFoundException();
        }

        User loginUser = userService.getUserById(user.getId());
        Optional<ChatRoomStatus> existing = chatRoomStatusRepository.findByUser_IdAndPattern_Id(loginUser.getId(), patternId);

        ChatRoomStatus status = existing.orElseGet(() -> chatRoomStatusRepository.save(
                ChatRoomStatus.builder()
                        .user(loginUser)
                        .pattern(pattern)
                        .favorite(request.favorite() != null ? request.favorite() : false)
                        .hidden(request.hidden() != null ? request.hidden() : false)
                        .build()
        ));

        if (existing.isPresent()) {
            status.update(request.favorite(), request.hidden());
        }

        return ChatRoomStatusResponse.of(patternId, status.isFavorite(), status.isHidden());
    }
}
