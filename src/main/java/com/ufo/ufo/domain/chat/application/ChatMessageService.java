package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatMessageItemResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatMessagesResponse;
import com.ufo.ufo.domain.chat.exception.ChatRoomForbiddenException;
import com.ufo.ufo.domain.chat.exception.ChatRoomNotFoundException;
import com.ufo.ufo.domain.chat.exception.ChatNicknameNotFoundException;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

        Map<Long, ChatRoomStatus> roomStatuses = findRoomStatuses(roomId, pageMessages);
        List<ChatMessageItemResponse> messages = pageMessages.stream()
                .map(message -> toResponse(message, roomStatuses))
                .toList();
        Long nextMessageId = hasNext && !pageMessages.isEmpty()
                ? pageMessages.getLast().getId()
                : null;

        Long lastMessageId = chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, loginUser.getId())
                .map(ChatReadStatus::getLastReadMessageId)
                .orElse(null);

        return ChatMessagesResponse.of(lastMessageId, hasNext, nextMessageId, messages);
    }

    private Map<Long, ChatRoomStatus> findRoomStatuses(Long roomId, List<ChatMessage> messages) {
        List<Long> userIds = messages.stream()
                .flatMap(message -> {
                    ChatMessage replyMessage = message.getReplyMessage();
                    return replyMessage == null
                            ? Stream.of(message.getUser().getId())
                            : Stream.of(message.getUser().getId(), replyMessage.getUser().getId());
                })
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return chatRoomStatusRepository.findAllByRoom_IdAndUser_IdIn(roomId, userIds).stream()
                .collect(Collectors.toMap(status -> status.getUser().getId(), Function.identity()));
    }

    private ChatMessageItemResponse toResponse(ChatMessage message, Map<Long, ChatRoomStatus> roomStatuses) {
        String senderName = getChatNickname(roomStatuses, message.getUser().getId());
        ChatMessage replyMessage = message.getReplyMessage();
        String replySenderName = replyMessage == null
                ? null
                : getChatNickname(roomStatuses, replyMessage.getUser().getId());
        return ChatMessageItemResponse.from(message, senderName, replySenderName);
    }

    private String getChatNickname(Map<Long, ChatRoomStatus> roomStatuses, Long userId) {
        ChatRoomStatus status = roomStatuses.get(userId);
        if (status == null || status.getNickname() == null || status.getNickname().isBlank()) {
            throw new ChatNicknameNotFoundException();
        }
        return status.getNickname();
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
