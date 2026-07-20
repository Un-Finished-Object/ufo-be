package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomLastMessage;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomUserCount;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomListResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount;
import com.ufo.ufo.domain.image.application.ImageService;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅방 쿼리 서비스 테스트")
class ChatRoomQueryServiceTest {

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserService userService;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ChatRoomQueryService chatRoomQueryService;

    @Test
    @DisplayName("참여 중인 채팅방이 없으면 빈 목록을 반환해야 한다")
    void getMyChats_NoChats_ReturnsEmpty() {
        User user = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        UserChatRoomListResponse response = chatRoomQueryService.getMyChats(user, 1);

        assertThat(response.chats()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPage()).isEqualTo(0);
    }

    @Test
    @DisplayName("채팅방 목록과 읽지 않은 메시지 수를 함께 반환해야 한다")
    void getMyChats_ReturnsChatListWithUnread() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern1 = PatternFixture.createPatternWithId(10L);
        Pattern pattern2 = PatternFixture.createPatternWithId(11L);
        ChatRoom room1 = ChatRoomFixture.createRoomWithId(pattern1, 100L);
        ChatRoom room2 = ChatRoomFixture.createRoomWithId(pattern2, 101L);

        ChatRoomStatus status1 = ChatRoomStatus.builder()
                .user(user)
                .room(room1)
                .favorite(true)
                .hidden(false)
                .build();
        ChatRoomStatus status2 = ChatRoomStatus.builder()
                .user(user)
                .room(room2)
                .favorite(false)
                .hidden(true)
                .build();

        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(status1, status2), PageRequest.of(0, 10), 2));
        when(chatRoomStatusRepository.countUfoUsersByRoomIds(List.of(100L, 101L)))
                .thenReturn(List.of(new ChatRoomUserCount(100L, 2L), new ChatRoomUserCount(101L, 1L)));
        when(chatMessageRepository.countUnreadByRoomIds(1L, List.of(100L, 101L)))
                .thenReturn(List.of(new ChatUnreadCount(100L, 3L)));
        when(chatMessageRepository.findLatestMessagesByRoomIds(List.of(100L, 101L)))
                .thenReturn(List.of(
                        new ChatRoomLastMessage(100L, "hello"),
                        new ChatRoomLastMessage(101L, "world")
                ));
        when(imageService.buildImageUrl("./patterns/1.png"))
                .thenReturn("https://cdn.example.com/patterns/1.png");

        UserChatRoomListResponse response = chatRoomQueryService.getMyChats(user, 1);

        assertThat(response.chats()).hasSize(2);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.nextPage()).isEqualTo(0);
        assertThat(response.chats().getFirst().patternId()).isEqualTo(10L);
        assertThat(response.chats().getFirst().chatId()).isEqualTo(100L);
        assertThat(response.chats().getFirst().favorite()).isTrue();
        assertThat(response.chats().getFirst().isHidden()).isFalse();
        assertThat(response.chats().getFirst().unRead()).isEqualTo(3);
        assertThat(response.chats().getFirst().userCount()).isEqualTo(2);
        assertThat(response.chats().getFirst().lastMessage()).isEqualTo("hello");
        assertThat(response.chats().getFirst().chatImageUrl())
                .isEqualTo("https://cdn.example.com/patterns/1.png");
        assertThat(response.chats().getFirst().createdAt()).isEqualTo(room1.getCreatedAt());
        assertThat(response.chats().get(1).patternId()).isEqualTo(11L);
        assertThat(response.chats().get(1).chatId()).isEqualTo(101L);
        assertThat(response.chats().get(1).unRead()).isEqualTo(0);
        assertThat(response.chats().get(1).userCount()).isEqualTo(1);
        assertThat(response.chats().get(1).lastMessage()).isEqualTo("world");
        assertThat(response.chats().get(1).chatImageUrl())
                .isEqualTo("https://cdn.example.com/patterns/1.png");
        assertThat(response.chats().get(1).createdAt()).isEqualTo(room2.getCreatedAt());
    }

    @Test
    @DisplayName("채팅방 목록은 page 기준으로 잘라서 반환해야 한다")
    void getMyChats_ReturnsPagedChatList() {
        User user = UserFixture.createUserWithId(1L);
        List<ChatRoomStatus> statuses = new ArrayList<>();
        List<Long> roomIdsOnSecondPage = List.of(110L);

        for (long index = 0; index < 11; index++) {
            long patternId = 10L + index;
            long roomId = 100L + index;
            Pattern pattern = PatternFixture.createPatternWithId(patternId);
            ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
            statuses.add(ChatRoomStatus.builder()
                    .user(user)
                    .room(room)
                    .favorite(false)
                    .hidden(false)
                    .build());
        }

        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(statuses.getLast()), PageRequest.of(1, 10), 11));
        when(chatRoomStatusRepository.countUfoUsersByRoomIds(roomIdsOnSecondPage))
                .thenReturn(List.of(new ChatRoomUserCount(110L, 1L)));
        when(chatMessageRepository.countUnreadByRoomIds(1L, roomIdsOnSecondPage))
                .thenReturn(List.of(new ChatUnreadCount(110L, 4L)));
        when(chatMessageRepository.findLatestMessagesByRoomIds(roomIdsOnSecondPage))
                .thenReturn(List.of(new ChatRoomLastMessage(110L, "latest")));

        UserChatRoomListResponse response = chatRoomQueryService.getMyChats(user, 2);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.nextPage()).isEqualTo(0);
        assertThat(response.chats()).hasSize(1);
        assertThat(response.chats().getFirst().chatId()).isEqualTo(110L);
        assertThat(response.chats().getFirst().lastMessage()).isEqualTo("latest");
        assertThat(response.chats().getFirst().userCount()).isEqualTo(1);
        assertThat(response.chats().getFirst().unRead()).isEqualTo(4);
        verify(chatRoomStatusRepository).findByUser_IdAndRoom_Pattern_DeletedAtIsNullOrderByCreatedAtDescIdDesc(
                eq(1L),
                eq(PageRequest.of(1, 10))
        );
    }
}
