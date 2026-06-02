package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomUserCount;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomItemResponse;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomListResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount;
import com.ufo.ufo.domain.pattern.domain.Pattern;
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
        List<ChatRoomStatus> statuses = chatRoomStatusRepository
                .findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(loginUser.getId());
        if (statuses.isEmpty()) {
            return UserChatRoomListResponse.of(Collections.emptyList());
        }

        List<Long> roomIds = statuses.stream()
                .map(ChatRoomStatus::getChatId)
                .toList();

        Map<Long, Long> unreadMap = chatMessageRepository.countUnreadByRoomIds(loginUser.getId(), roomIds)
                .stream()
                .collect(Collectors.toMap(ChatUnreadCount::chatId, ChatUnreadCount::unRead));
        Map<Long, Long> userCountMap = chatRoomStatusRepository.countUfoUsersByRoomIds(roomIds)
                .stream()
                .collect(Collectors.toMap(ChatRoomUserCount::chatId, ChatRoomUserCount::userCount));

        List<UserChatRoomItemResponse> chats = statuses.stream()
                .map(status -> {
                    Long chatId = status.getChatId();
                    int unRead = unreadMap.getOrDefault(chatId, 0L).intValue();
                    int userCount = userCountMap.getOrDefault(chatId, 0L).intValue();
                    ChatRoom room = status.getRoom();
                    Pattern pattern = room.getPattern();
                    return UserChatRoomItemResponse.of(
                            pattern.getId(),
                            chatId,
                            pattern.getTitle(),
                            pattern.getThumbnailUrl(),
                            status.isFavorite(),
                            status.isHidden(),
                            unRead,
                            userCount,
                            room.getCreatedAt()
                    );
                })
                .toList();

        return UserChatRoomListResponse.of(chats);
    }
}
