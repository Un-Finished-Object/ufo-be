package com.ufo.ufo.domain.chat.application;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.dto.response.AdminCheckChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminDeleteChatMessageResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomInfo;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomItemResponse;
import com.ufo.ufo.domain.chat.dto.response.AdminUnreadChatRoomListResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomLastMessage;
import com.ufo.ufo.domain.chat.exception.ChatRoomForbiddenException;
import com.ufo.ufo.domain.chat.exception.ChatRoomNotFoundException;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.security.types.Role;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminChatService {

    private static final int PAGE_SIZE = 20;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final UserService userService;
    private final ImageService imageService;

    public AdminUnreadChatRoomListResponse getUnreadChatRooms(User user, Integer page) {
        User adminUser = userService.getUserById(user.getId());
        validateAdminRole(adminUser);
        int pageNumber = normalizePage(page);
        Page<AdminUnreadChatRoomInfo> infoPage = chatRoomRepository.findUnreadChatRoomsForAdmin(
                adminUser.getId(),
                PageRequest.of(pageNumber - 1, PAGE_SIZE)
        );

        if (infoPage.isEmpty()) {
            return AdminUnreadChatRoomListResponse.of(Collections.emptyList(), pageNumber, 0);
        }

        int totalPages = infoPage.getTotalPages();
        List<AdminUnreadChatRoomInfo> infos = infoPage.getContent();
        List<Long> roomIds = infos.stream().map(AdminUnreadChatRoomInfo::chatId).toList();

        Map<Long, String> lastMessageMap = chatMessageRepository.findLatestMessagesByRoomIds(roomIds)
                .stream()
                .collect(Collectors.toMap(ChatRoomLastMessage::chatId, ChatRoomLastMessage::lastMessage));

        List<AdminUnreadChatRoomItemResponse> chats = infos.stream()
                .map(info -> new AdminUnreadChatRoomItemResponse(
                        info.chatId(),
                        info.patternId(),
                        info.chatName(),
                        imageService.buildImageUrl(info.thumbnailUrl()),
                        info.unRead().intValue(),
                        lastMessageMap.get(info.chatId()),
                        info.lastMessageAt(),
                        info.createdAt()
                ))
                .toList();

        return AdminUnreadChatRoomListResponse.of(chats, pageNumber, resolveNextPage(pageNumber, totalPages));
    }

    @Transactional
    public AdminDeleteChatMessageResponse deleteMessage(User user, Long chatRoomId, Long messageId) {
        User adminUser = userService.getUserById(user.getId());
        validateAdminRole(adminUser);
        chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(chatRoomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        ChatMessage chatMessage = chatMessageRepository.findByIdAndRoom_Id(messageId, chatRoomId)
                .orElseThrow(InvalidChatMessageIdException::new);

        LocalDateTime now = LocalDateTime.now();
        chatMessage.delete(now);

        return AdminDeleteChatMessageResponse.of(chatRoomId, messageId, chatMessage.getDeletedAt());
    }

    @Transactional
    public AdminCheckChatMessageResponse checkMessage(User user, Long chatRoomId, Long messageId) {
        User adminUser = userService.getUserById(user.getId());
        validateAdminRole(adminUser);
        ChatRoom room = chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(chatRoomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        chatMessageRepository.findByIdAndRoom_Id(messageId, chatRoomId)
                .orElseThrow(InvalidChatMessageIdException::new);

        LocalDateTime now = LocalDateTime.now();
        chatReadStatusRepository.findByRoom_IdAndUser_Id(chatRoomId, adminUser.getId())
                .ifPresentOrElse(
                        readStatus -> {
                            Long currentLastRead = readStatus.getLastReadMessageId();
                            Long newLastRead = currentLastRead == null ? messageId : Math.max(currentLastRead, messageId);
                            readStatus.update(newLastRead, now);
                        },
                        () -> chatReadStatusRepository.save(ChatReadStatus.builder()
                                .room(room)
                                .user(adminUser)
                                .lastReadMessageId(messageId)
                                .readAt(now)
                                .build())
                );

        return AdminCheckChatMessageResponse.of(chatRoomId, messageId, now);
    }

    private void validateAdminRole(User user) {
        if (user.getRole() != Role.ROLE_ADMIN) {
            throw new ChatRoomForbiddenException();
        }
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
