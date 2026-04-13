package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatMessageItemResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatMessagesResponse;
import com.ufo.ufo.domain.chat.exception.ChatRoomForbiddenException;
import com.ufo.ufo.domain.chat.exception.ChatRoomNotFoundException;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private static final int MESSAGE_PAGE_SIZE = 30;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final UserService userService;

    public ChatMessagesResponse getMessages(User user, Long roomId, Long messageId) {
        chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        User loginUser = userService.getUserById(user.getId());
        validateRoomAccess(loginUser.getId(), roomId);
        PageRequest pageable = PageRequest.of(0, MESSAGE_PAGE_SIZE + 1);

        List<ChatMessage> fetchedMessages = findMessages(roomId, messageId, pageable);
        boolean hasNext = fetchedMessages.size() > MESSAGE_PAGE_SIZE;
        List<ChatMessage> pageMessages = hasNext
                ? fetchedMessages.subList(0, MESSAGE_PAGE_SIZE)
                : fetchedMessages;

        List<ChatMessageItemResponse> messages = pageMessages.stream()
                .map(ChatMessageItemResponse::from)
                .toList();
        Long nextMessageId = hasNext && !pageMessages.isEmpty()
                ? pageMessages.getLast().getId()
                : null;

        Long lastMessageId = chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, loginUser.getId())
                .map(ChatReadStatus::getLastReadMessageId)
                .orElse(null);

        return ChatMessagesResponse.of(lastMessageId, hasNext, nextMessageId, messages);
    }

    private List<ChatMessage> findMessages(Long roomId, Long messageId, PageRequest pageable) {
        if (messageId == null) {
            return chatMessageRepository.findByRoom_IdOrderByIdDesc(roomId, pageable);
        }
        if (messageId <= 0) {
            throw new InvalidChatMessageIdException();
        }
        return chatMessageRepository.findByRoom_IdAndIdLessThanOrderByIdDesc(roomId, messageId, pageable);
    }

    private void validateRoomAccess(Long userId, Long roomId) {
        boolean hasAccess = chatRoomStatusRepository.findByUser_IdAndRoom_Id(userId, roomId).isPresent();
        if (!hasAccess) {
            throw new ChatRoomForbiddenException();
        }
    }
}
