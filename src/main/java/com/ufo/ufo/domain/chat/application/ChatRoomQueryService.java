package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomItemResponse;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomListResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomQueryService {

    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;

    public UserChatRoomListResponse getMyChats(User user) {
        User loginUser = userService.getUserById(user.getId());
        List<ChatRoomStatus> statuses = chatRoomStatusRepository.findAllActiveByUserIdOrderByLatest(loginUser.getId());
        if (statuses.isEmpty()) {
            return UserChatRoomListResponse.of(Collections.emptyList());
        }

        List<Long> patternIds = statuses.stream()
                .map(ChatRoomStatus::getChatId)
                .toList();

        Map<Long, Long> unreadMap = chatMessageRepository.countUnreadByPatternIds(loginUser.getId(), patternIds)
                .stream()
                .collect(Collectors.toMap(ChatUnreadCount::chatId, ChatUnreadCount::unRead));

        List<UserChatRoomItemResponse> chats = statuses.stream()
                .map(status -> {
                    Long chatId = status.getChatId();
                    int unRead = unreadMap.getOrDefault(chatId, 0L).intValue();
                    return UserChatRoomItemResponse.of(
                            chatId,
                            status.getPattern().getTitle(),
                            status.getPattern().getThumbnailUrl(),
                            status.isFavorite(),
                            status.isHidden(),
                            unRead
                    );
                })
                .toList();

        return UserChatRoomListResponse.of(chats);
    }
}
