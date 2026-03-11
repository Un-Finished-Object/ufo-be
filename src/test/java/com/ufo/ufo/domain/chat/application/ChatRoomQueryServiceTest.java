package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.UserChatRoomListResponse;
import com.ufo.ufo.domain.chat.dto.response.ChatUnreadCount;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅방 쿼리 서비스 테스트")
class ChatRoomQueryServiceTest {

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatRoomQueryService chatRoomQueryService;

    @Test
    @DisplayName("참여 중인 채팅방이 없으면 빈 목록을 반환해야 한다")
    void getMyChats_NoChats_ReturnsEmpty() {
        User user = UserFixture.createUserWithId(1L);
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findAllActiveByUserIdOrderByLatest(1L)).thenReturn(List.of());

        UserChatRoomListResponse response = chatRoomQueryService.getMyChats(user);

        assertThat(response.chats()).isEmpty();
    }

    @Test
    @DisplayName("채팅방 목록과 읽지 않은 메시지 수를 함께 반환해야 한다")
    void getMyChats_ReturnsChatListWithUnread() {
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern1 = PatternFixture.createPatternWithId(10L);
        Pattern pattern2 = PatternFixture.createPatternWithId(11L);

        ChatRoomStatus status1 = ChatRoomStatus.builder()
                .user(user)
                .pattern(pattern1)
                .favorite(true)
                .hidden(false)
                .build();
        ChatRoomStatus status2 = ChatRoomStatus.builder()
                .user(user)
                .pattern(pattern2)
                .favorite(false)
                .hidden(true)
                .build();

        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findAllActiveByUserIdOrderByLatest(1L)).thenReturn(List.of(status1, status2));
        when(chatMessageRepository.countUnreadByPatternIds(1L, List.of(10L, 11L)))
                .thenReturn(List.of(new ChatUnreadCount(10L, 3L)));

        UserChatRoomListResponse response = chatRoomQueryService.getMyChats(user);

        assertThat(response.chats()).hasSize(2);
        assertThat(response.chats().getFirst().chatId()).isEqualTo(10L);
        assertThat(response.chats().getFirst().favorite()).isTrue();
        assertThat(response.chats().getFirst().isHidden()).isFalse();
        assertThat(response.chats().getFirst().unRead()).isEqualTo(3);
        assertThat(response.chats().getFirst().chatImageUrl()).isEqualTo(pattern1.getThumbnailUrl());
        assertThat(response.chats().get(1).chatId()).isEqualTo(11L);
        assertThat(response.chats().get(1).unRead()).isEqualTo(0);
        assertThat(response.chats().get(1).chatImageUrl()).isEqualTo(pattern2.getThumbnailUrl());
    }
}
