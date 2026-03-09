package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatMessageItemResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatMessagesResponse;
import com.ufo.ufo.domain.chat.exception.ChatRoomNotFoundException;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
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
    private final PatternRepository patternRepository;
    private final UserService userService;

    public ChatMessagesResponse getMessages(User user, Long patternId, Long messageId) {
        if (!patternRepository.existsById(patternId)) {
            throw new ChatRoomNotFoundException();
        }

        User loginUser = userService.getUserById(user.getId());
        PageRequest pageable = PageRequest.of(0, MESSAGE_PAGE_SIZE + 1);

        List<ChatMessage> fetchedMessages = findMessages(patternId, messageId, pageable);
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

        Long lastMessageId = chatReadStatusRepository.findByPattern_IdAndUser_Id(patternId, loginUser.getId())
                .map(ChatReadStatus::getLastReadMessageId)
                .orElse(null);

        return ChatMessagesResponse.of(lastMessageId, hasNext, nextMessageId, messages);
    }

    private List<ChatMessage> findMessages(Long patternId, Long messageId, PageRequest pageable) {
        if (messageId == null) {
            return chatMessageRepository.findByPattern_IdOrderByIdDesc(patternId, pageable);
        }
        if (messageId <= 0) {
            throw new InvalidChatMessageIdException();
        }
        return chatMessageRepository.findByPattern_IdAndIdLessThanOrderByIdDesc(patternId, messageId, pageable);
    }
}
