package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomLastMessage;
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

    private static final int PAGE_SIZE = 10;

    private final ChatRoomStatusRepository chatRoomStatusRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;

    public UserChatRoomListResponse getMyChats(User user, Integer page) {
        User loginUser = userService.getUserById(user.getId());
        int pageNumber = normalizePage(page);
        List<ChatRoomStatus> statuses = chatRoomStatusRepository
                .findAllByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(loginUser.getId());
        if (statuses.isEmpty()) {
            return UserChatRoomListResponse.of(Collections.emptyList(), pageNumber, 0);
        }

        int totalPages = (int) Math.ceil((double) statuses.size() / PAGE_SIZE);
        if (pageNumber > totalPages) {
            return UserChatRoomListResponse.of(Collections.emptyList(), pageNumber, 0);
        }

        int fromIndex = (pageNumber - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, statuses.size());
        List<ChatRoomStatus> pagedStatuses = statuses.subList(fromIndex, toIndex);
        List<Long> roomIds = pagedStatuses.stream()
                .map(ChatRoomStatus::getChatId)
                .toList();

        Map<Long, Long> unreadMap = chatMessageRepository.countUnreadByRoomIds(loginUser.getId(), roomIds)
                .stream()
                .collect(Collectors.toMap(ChatUnreadCount::chatId, ChatUnreadCount::unRead));
        Map<Long, Long> userCountMap = chatRoomStatusRepository.countUfoUsersByRoomIds(roomIds)
                .stream()
                .collect(Collectors.toMap(ChatRoomUserCount::chatId, ChatRoomUserCount::userCount));
        Map<Long, String> lastMessageMap = chatMessageRepository.findLatestMessagesByRoomIds(roomIds)
                .stream()
                .collect(Collectors.toMap(ChatRoomLastMessage::chatId, ChatRoomLastMessage::lastMessage));

        List<UserChatRoomItemResponse> chats = pagedStatuses.stream()
                .map(status -> {
                    Long chatId = status.getChatId();
                    int unRead = unreadMap.getOrDefault(chatId, 0L).intValue();
                    int userCount = userCountMap.getOrDefault(chatId, 0L).intValue();
                    String lastMessage = lastMessageMap.get(chatId);
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
                            lastMessage,
                            room.getCreatedAt()
                    );
                })
                .toList();

        return UserChatRoomListResponse.of(chats, pageNumber, resolveNextPage(pageNumber, totalPages));
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int resolveNextPage(int currentPage, int totalPages) {
        int remainingPages = totalPages - currentPage;
        if (remainingPages <= 0) {
            return 0;
        }
        return Math.min(remainingPages, 5);
    }
}
