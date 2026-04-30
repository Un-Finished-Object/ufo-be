package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.response.ChatMessagesResponse;
import com.ufo.ufo.domain.chat.exception.InvalidChatMessageIdException;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.application.UserService;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
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
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("messageId가 없으면 최신 메시지 목록과 마지막 읽음 메시지 ID를 반환해야 한다")
    void getMessages_WithoutCursor_ReturnsRecentMessages() {
        Long roomId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus roomStatus = ChatRoomStatus.builder()
                .user(user)
                .room(room)
                .favorite(false)
                .hidden(false)
                .build();

        ChatMessage message = ChatMessage.builder()
                .room(room)
                .user(user)
                .text("안녕하세요")
                .build();
        setId(message, 50L);
        setCreatedAt(message, LocalDateTime.of(2026, 3, 9, 12, 0));

        ChatReadStatus readStatus = ChatReadStatus.builder()
                .room(room)
                .user(user)
                .lastReadMessageId(38L)
                .readAt(LocalDateTime.of(2026, 3, 9, 12, 1))
                .build();

        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(1L, roomId)).thenReturn(Optional.of(roomStatus));
        when(chatMessageRepository.findByRoom_IdOrderByIdDesc(roomId, PageRequest.of(0, 31)))
                .thenReturn(List.of(message));
        when(chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, 1L))
                .thenReturn(Optional.of(readStatus));

        ChatMessagesResponse response = chatMessageService.getMessages(user, roomId, null);

        assertThat(response.lastMessageId()).isEqualTo(38L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextMessageId()).isNull();
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().senderId()).isEqualTo(1L);
        assertThat(response.messages().getFirst().senderName()).isEqualTo("tester");
        assertThat(response.messages().getFirst().messageId()).isEqualTo(50L);
        assertThat(response.messages().getFirst().text()).isEqualTo("안녕하세요");
        assertThat(response.messages().getFirst().replySenderName()).isNull();
        assertThat(response.messages().getFirst().replyMessageId()).isNull();
        assertThat(response.messages().getFirst().createdAt()).isEqualTo(LocalDateTime.of(2026, 3, 9, 12, 0));
    }

    @Test
    @DisplayName("답장 메시지는 원본 메시지 ID와 원본 발신자 이름을 함께 반환해야 한다")
    void getMessages_WithReply_ReturnsReplyMetadata() {
        Long roomId = 10L;
        User user = UserFixture.createUserWithId(1L);
        User replySender = UserFixture.createUserWithId(2L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus roomStatus = ChatRoomStatus.builder()
                .user(user)
                .room(room)
                .favorite(false)
                .hidden(false)
                .build();

        ChatMessage replyMessage = ChatMessage.builder()
                .room(room)
                .user(replySender)
                .text("원본 메시지")
                .build();
        setId(replyMessage, 49L);

        ChatMessage message = ChatMessage.builder()
                .room(room)
                .user(user)
                .text("답장 메시지")
                .replyMessage(replyMessage)
                .build();
        setId(message, 50L);
        setCreatedAt(message, LocalDateTime.of(2026, 3, 9, 12, 0));

        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(1L, roomId)).thenReturn(Optional.of(roomStatus));
        when(chatMessageRepository.findByRoom_IdOrderByIdDesc(roomId, PageRequest.of(0, 31)))
                .thenReturn(List.of(message));
        when(chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, 1L))
                .thenReturn(Optional.empty());

        ChatMessagesResponse response = chatMessageService.getMessages(user, roomId, null);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().replySenderName()).isEqualTo("tester");
        assertThat(response.messages().getFirst().replyMessageId()).isEqualTo(49L);
    }

    @Test
    @DisplayName("messageId가 있으면 해당 ID 이전 메시지 목록을 반환해야 한다")
    void getMessages_WithCursor_ReturnsOlderMessages() {
        Long roomId = 10L;
        Long cursorMessageId = 50L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus roomStatus = ChatRoomStatus.builder()
                .user(user)
                .room(room)
                .favorite(false)
                .hidden(false)
                .build();

        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(1L, roomId)).thenReturn(Optional.of(roomStatus));
        when(chatMessageRepository.findByRoom_IdAndIdLessThanOrderByIdDesc(roomId, cursorMessageId, PageRequest.of(0, 31)))
                .thenReturn(List.of());
        when(chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, 1L))
                .thenReturn(Optional.empty());

        ChatMessagesResponse response = chatMessageService.getMessages(user, roomId, cursorMessageId);

        assertThat(response.lastMessageId()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextMessageId()).isNull();
        assertThat(response.messages()).isEmpty();
        verify(chatMessageRepository).findByRoom_IdAndIdLessThanOrderByIdDesc(roomId, cursorMessageId, PageRequest.of(0, 31));
    }

    @Test
    @DisplayName("조회 결과가 페이지 크기를 초과하면 hasNext=true와 nextMessageId를 반환해야 한다")
    void getMessages_HasNext_ReturnsNextCursor() {
        Long roomId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus roomStatus = ChatRoomStatus.builder()
                .user(user)
                .room(room)
                .favorite(false)
                .hidden(false)
                .build();
        List<ChatMessage> fetchedMessages = new ArrayList<>();
        for (long id = 100L; id >= 70L; id--) {
            ChatMessage chatMessage = ChatMessage.builder()
                    .room(room)
                    .user(user)
                    .text("msg-" + id)
                    .build();
            setId(chatMessage, id);
            setCreatedAt(chatMessage, LocalDateTime.of(2026, 3, 9, 12, 0));
            fetchedMessages.add(chatMessage);
        }

        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(1L, roomId)).thenReturn(Optional.of(roomStatus));
        when(chatMessageRepository.findByRoom_IdOrderByIdDesc(roomId, PageRequest.of(0, 31)))
                .thenReturn(fetchedMessages);
        when(chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, 1L))
                .thenReturn(Optional.empty());

        ChatMessagesResponse response = chatMessageService.getMessages(user, roomId, null);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.messages()).hasSize(30);
        assertThat(response.nextMessageId()).isEqualTo(71L);
    }

    @Test
    @DisplayName("messageId가 0 이하이면 InvalidChatMessageIdException을 던져야 한다")
    void getMessages_InvalidCursor_ThrowsException() {
        Long roomId = 10L;
        User user = UserFixture.createUserWithId(1L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus roomStatus = ChatRoomStatus.builder()
                .user(user)
                .room(room)
                .favorite(false)
                .hidden(false)
                .build();

        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(userService.getUserById(1L)).thenReturn(user);
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(1L, roomId)).thenReturn(Optional.of(roomStatus));

        assertThatThrownBy(() -> chatMessageService.getMessages(user, roomId, 0L))
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
