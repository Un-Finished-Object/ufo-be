package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.request.UpdateChatRoomStatusRequest;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomStatusResponse;
import com.ufo.ufo.domain.chat.exception.InvalidChatRoomStatusUpdateException;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅방 상태 서비스 테스트")
class ChatRoomStatusServiceTest {

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatRoomStatusService chatRoomStatusService;

    @Test
    @DisplayName("상태가 존재하면 요청한 필드만 업데이트해야 한다")
    void updateStatus_UpdateExistingStatus() {
        Long roomId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus existing = ChatRoomStatus.builder()
                .user(user)
                .room(room)
                .favorite(true)
                .hidden(false)
                .build();

        when(chatRoomRepository.existsByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(true);
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(1L, roomId)).thenReturn(Optional.of(existing));

        ChatRoomStatusResponse response = chatRoomStatusService.updateStatus(
                user,
                roomId,
                new UpdateChatRoomStatusRequest(null, true)
        );

        assertThat(response.chatId()).isEqualTo(roomId);
        assertThat(response.favorite()).isTrue();
        assertThat(response.isHidden()).isTrue();
        verify(chatRoomStatusRepository).findByUser_IdAndRoom_Id(1L, roomId);
    }

    @Test
    @DisplayName("요청 필드가 없으면 예외를 던져야 한다")
    void updateStatus_NoFields_ThrowsException() {
        Long roomId = 10L;
        User user = UserFixture.createUserWithId(1L);

        assertThatThrownBy(() -> chatRoomStatusService.updateStatus(
                user,
                roomId,
                new UpdateChatRoomStatusRequest(null, null)
        )).isInstanceOf(InvalidChatRoomStatusUpdateException.class);
    }
}
