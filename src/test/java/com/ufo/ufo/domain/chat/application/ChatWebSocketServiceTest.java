package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatErrorPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatEventType;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatSocketEvent;
import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomRepository;
import com.ufo.ufo.domain.chat.dao.ChatRoomStatusRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.pattern.domain.Pattern;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.lang.reflect.Field;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 WebSocket 서비스 테스트")
class ChatWebSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomStatusRepository chatRoomStatusRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatReadStatusRepository chatReadStatusRepository;

    @InjectMocks
    private ChatWebSocketService chatWebSocketService;

    @Test
    @DisplayName("메시지 전송 시 해당 채팅방으로 MESSAGE_CREATED 이벤트를 브로드캐스트해야 한다")
    void publishMessage_BroadcastsMessageCreatedEvent() {
        Long roomId = 10L;
        String userEmail = "test@example.com";
        User user = UserFixture.createUser(userEmail, com.ufo.ufo.global.security.types.Role.ROLE_USER);
        UserFixture.setId(user, 21L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus roomStatus = ChatRoomStatus.builder()
            .user(user)
            .room(room)
            .favorite(false)
            .hidden(false)
            .build();
        ChatMessage savedMessage = ChatMessage.builder()
            .room(room)
            .user(user)
            .text("안녕하세요")
            .build();
        setId(savedMessage, 1L);
        setCreatedAt(savedMessage, LocalDateTime.of(2026, 3, 9, 10, 0));
        Principal principal = () -> userEmail;

        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(21L, roomId)).thenReturn(Optional.of(roomStatus));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        chatWebSocketService.publishMessage(principal,
                new ChatMessageSendRequest(roomId, " 안녕하세요 ", "temp-123456"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/10"), payloadCaptor.capture());

        ChatSocketEvent<?> event = (ChatSocketEvent<?>) payloadCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.MESSAGE_CREATED);
        assertThat(event.roomId()).isEqualTo(roomId);

        ChatMessageCreatedPayload payload = (ChatMessageCreatedPayload) event.payload();
        assertThat(payload.messageId()).isEqualTo(1L);
        assertThat(payload.clientMessageId()).isEqualTo("temp-123456");
        assertThat(payload.senderId()).isEqualTo(21L);
        assertThat(payload.senderName()).isEqualTo(user.getNickname());
        assertThat(payload.text()).isEqualTo("안녕하세요");
        assertThat(payload.createdAt()).isEqualTo(LocalDateTime.of(2026, 3, 9, 10, 0));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방으로 메시지 전송 시 ERROR 이벤트를 브로드캐스트해야 한다")
    void publishMessage_InvalidRoom_BroadcastsErrorEvent() {
        Long roomId = 999L;
        String userEmail = "test@example.com";
        Principal principal = () -> userEmail;
        User user = UserFixture.createUser(userEmail, com.ufo.ufo.global.security.types.Role.ROLE_USER);
        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));

        chatWebSocketService.publishMessage(principal,
                new ChatMessageSendRequest(roomId, "안녕하세요", "temp-err-1"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/999"), payloadCaptor.capture());

        ChatSocketEvent<?> event = (ChatSocketEvent<?>) payloadCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.ERROR);
        assertThat(event.roomId()).isEqualTo(roomId);

        ChatErrorPayload payload = (ChatErrorPayload) event.payload();
        assertThat(payload.code()).isEqualTo("CHAT_ROOM_NOT_FOUND");
        assertThat(payload.message()).isEqualTo("존재하지 않는 채팅방입니다.");
        assertThat(payload.clientMessageId()).isEqualTo("temp-err-1");
    }

    @Test
    @DisplayName("읽음 처리 전송 시 해당 채팅방으로 READ_UPDATED 이벤트를 브로드캐스트해야 한다")
    void publishReadUpdate_BroadcastsReadUpdatedEvent() {
        Long roomId = 10L;
        String userEmail = "test@example.com";
        User user = UserFixture.createUser(userEmail, com.ufo.ufo.global.security.types.Role.ROLE_USER);
        UserFixture.setId(user, 21L);
        Pattern pattern = PatternFixture.createPatternWithId(100L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(pattern, roomId);
        ChatRoomStatus roomStatus = ChatRoomStatus.builder().user(user).room(room).favorite(false).hidden(false).build();
        Principal principal = () -> userEmail;

        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(chatRoomStatusRepository.findByUser_IdAndRoom_Id(21L, roomId)).thenReturn(Optional.of(roomStatus));
        when(chatReadStatusRepository.findByRoom_IdAndUser_Id(roomId, 21L)).thenReturn(Optional.empty());

        chatWebSocketService.publishReadUpdate(principal, new ChatReadUpdateRequest(roomId, 53L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/10"), payloadCaptor.capture());

        ChatSocketEvent<?> event = (ChatSocketEvent<?>) payloadCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.READ_UPDATED);
        assertThat(event.roomId()).isEqualTo(roomId);

        ChatReadUpdatedPayload payload = (ChatReadUpdatedPayload) event.payload();
        assertThat(payload.userId()).isEqualTo(21L);
        assertThat(payload.lastReadMessageId()).isEqualTo(53L);
        assertThat(payload.readAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 채팅방으로 읽음 처리 전송 시 ERROR 이벤트를 브로드캐스트해야 한다")
    void publishReadUpdate_InvalidRoom_BroadcastsErrorEvent() {
        Long roomId = 999L;
        String userEmail = "test@example.com";
        Principal principal = () -> userEmail;
        User user = UserFixture.createUser(userEmail, com.ufo.ufo.global.security.types.Role.ROLE_USER);
        when(chatRoomRepository.findByIdAndPattern_DeletedAtIsNull(roomId)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));

        chatWebSocketService.publishReadUpdate(principal, new ChatReadUpdateRequest(roomId, 53L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/rooms/999"), payloadCaptor.capture());

        ChatSocketEvent<?> event = (ChatSocketEvent<?>) payloadCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ChatEventType.ERROR);
        assertThat(event.roomId()).isEqualTo(roomId);

        ChatErrorPayload payload = (ChatErrorPayload) event.payload();
        assertThat(payload.code()).isEqualTo("CHAT_ROOM_NOT_FOUND");
        assertThat(payload.message()).isEqualTo("존재하지 않는 채팅방입니다.");
        assertThat(payload.clientMessageId()).isNull();
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
