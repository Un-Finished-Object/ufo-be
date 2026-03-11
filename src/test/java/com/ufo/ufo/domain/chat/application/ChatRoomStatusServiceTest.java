package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.request.UpdateChatRoomStatusRequest;
import com.ufo.ufo.domain.chat.dto.response.ChatRoomStatusResponse;
import com.ufo.ufo.domain.chat.exception.InvalidChatRoomStatusUpdateException;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.lang.reflect.Field;
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
    private PatternRepository patternRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatRoomStatusService chatRoomStatusService;

    @Test
    @DisplayName("상태가 없으면 요청 값으로 생성하고 응답해야 한다")
    void updateStatus_CreateNewStatus() {
        Long patternId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(patternId);
        ChatRoomStatus saved = ChatRoomStatus.builder()
                .user(user)
                .pattern(pattern)
                .favorite(true)
                .hidden(false)
                .build();
        setId(saved, 100L);

        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndPattern_Id(1L, patternId)).thenReturn(Optional.empty());
        when(chatRoomStatusRepository.save(org.mockito.ArgumentMatchers.any(ChatRoomStatus.class))).thenReturn(saved);

        ChatRoomStatusResponse response = chatRoomStatusService.updateStatus(
                user,
                patternId,
                new UpdateChatRoomStatusRequest(true, null)
        );

        assertThat(response.chatId()).isEqualTo(patternId);
        assertThat(response.favorite()).isTrue();
        assertThat(response.isHidden()).isFalse();
    }

    @Test
    @DisplayName("상태가 존재하면 요청한 필드만 업데이트해야 한다")
    void updateStatus_UpdateExistingStatus() {
        Long patternId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(patternId);
        ChatRoomStatus existing = ChatRoomStatus.builder()
                .user(user)
                .pattern(pattern)
                .favorite(true)
                .hidden(false)
                .build();

        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndPattern_Id(1L, patternId)).thenReturn(Optional.of(existing));

        ChatRoomStatusResponse response = chatRoomStatusService.updateStatus(
                user,
                patternId,
                new UpdateChatRoomStatusRequest(null, true)
        );

        assertThat(response.favorite()).isTrue();
        assertThat(response.isHidden()).isTrue();
        verify(chatRoomStatusRepository).findByUser_IdAndPattern_Id(1L, patternId);
    }

    @Test
    @DisplayName("요청 필드가 없으면 예외를 던져야 한다")
    void updateStatus_NoFields_ThrowsException() {
        Long patternId = 10L;
        User user = UserFixture.createUserWithId(1L);

        assertThatThrownBy(() -> chatRoomStatusService.updateStatus(
                user,
                patternId,
                new UpdateChatRoomStatusRequest(null, null)
        )).isInstanceOf(InvalidChatRoomStatusUpdateException.class);
    }

    private void setId(ChatRoomStatus status, Long id) {
        try {
            Field idField = ChatRoomStatus.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(status, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
