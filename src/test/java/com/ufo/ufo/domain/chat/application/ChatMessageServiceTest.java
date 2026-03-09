package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatMessagesResponse;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.pattern.dao.PatternRepository;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 메시지 서비스 테스트")
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatReadStatusRepository chatReadStatusRepository;

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("messageId가 없으면 최신 메시지 목록과 마지막 읽음 메시지 ID를 반환해야 한다")
    void getMessages_WithoutCursor_ReturnsRecentMessages() {
        Long patternId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(patternId);

        ChatMessage message = ChatMessage.builder()
                .pattern(pattern)
                .user(user)
                .text("안녕하세요")
                .build();
        setId(message, 50L);
        setCreatedAt(message, LocalDateTime.of(2026, 3, 9, 12, 0));

        ChatReadStatus readStatus = ChatReadStatus.builder()
                .pattern(pattern)
                .user(user)
                .lastReadMessageId(38L)
                .readAt(LocalDateTime.of(2026, 3, 9, 12, 1))
                .build();

        when(patternRepository.existsById(patternId)).thenReturn(true);
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatMessageRepository.findByPattern_IdOrderByIdDesc(patternId, PageRequest.of(0, 31)))
                .thenReturn(List.of(message));
        when(chatReadStatusRepository.findByPattern_IdAndUser_Id(patternId, 1L))
                .thenReturn(Optional.of(readStatus));

        ChatMessagesResponse response = chatMessageService.getMessages(user, patternId, null);

        assertThat(response.lastMessageId()).isEqualTo(38L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextMessageId()).isNull();
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).messageId()).isEqualTo(50L);
        assertThat(response.messages().get(0).text()).isEqualTo("안녕하세요");
        assertThat(response.messages().get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 3, 9, 12, 0));
    }

    @Test
    @DisplayName("messageId가 있으면 해당 ID 이전 메시지 목록을 반환해야 한다")
    void getMessages_WithCursor_ReturnsOlderMessages() {
        Long patternId = 10L;
        Long cursorMessageId = 50L;
        User user = UserFixture.createUserWithId(1L);

        when(patternRepository.existsById(patternId)).thenReturn(true);
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatMessageRepository.findByPattern_IdAndIdLessThanOrderByIdDesc(patternId, cursorMessageId, PageRequest.of(0, 31)))
                .thenReturn(List.of());
        when(chatReadStatusRepository.findByPattern_IdAndUser_Id(patternId, 1L))
                .thenReturn(Optional.empty());

        ChatMessagesResponse response = chatMessageService.getMessages(user, patternId, cursorMessageId);

        assertThat(response.lastMessageId()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextMessageId()).isNull();
        assertThat(response.messages()).isEmpty();
        verify(chatMessageRepository).findByPattern_IdAndIdLessThanOrderByIdDesc(patternId, cursorMessageId, PageRequest.of(0, 31));
    }

    @Test
    @DisplayName("조회 결과가 페이지 크기를 초과하면 hasNext=true와 nextMessageId를 반환해야 한다")
    void getMessages_HasNext_ReturnsNextCursor() {
        Long patternId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(patternId);
        List<ChatMessage> fetchedMessages = new ArrayList<>();
        for (long id = 100L; id >= 70L; id--) {
            ChatMessage chatMessage = ChatMessage.builder()
                    .pattern(pattern)
                    .user(user)
                    .text("msg-" + id)
                    .build();
            setId(chatMessage, id);
            setCreatedAt(chatMessage, LocalDateTime.of(2026, 3, 9, 12, 0));
            fetchedMessages.add(chatMessage);
        }

        when(patternRepository.existsById(patternId)).thenReturn(true);
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatMessageRepository.findByPattern_IdOrderByIdDesc(patternId, PageRequest.of(0, 31)))
                .thenReturn(fetchedMessages);
        when(chatReadStatusRepository.findByPattern_IdAndUser_Id(patternId, 1L))
                .thenReturn(Optional.empty());

        ChatMessagesResponse response = chatMessageService.getMessages(user, patternId, null);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.messages()).hasSize(30);
        assertThat(response.nextMessageId()).isEqualTo(71L);
    }

    @Test
    @DisplayName("messageId가 0 이하이면 InvalidChatMessageIdException을 던져야 한다")
    void getMessages_InvalidCursor_ThrowsException() {
        Long patternId = 10L;
        User user = UserFixture.createUserWithId(1L);

        when(patternRepository.existsById(patternId)).thenReturn(true);
        when(userService.getUserById(1L)).thenReturn(user);

        assertThatThrownBy(() -> chatMessageService.getMessages(user, patternId, 0L))
                .isInstanceOf(InvalidChatMessageIdException.class);
    }

    private void setId(ChatMessage chatMessage, Long id) {
        try {
            Field idField = ChatMessage.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(chatMessage, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setCreatedAt(ChatMessage chatMessage, LocalDateTime createdAt) {
        try {
            Field createdAtField = ChatMessage.class.getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(chatMessage, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
