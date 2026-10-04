package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatReadStatusRepository;
import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatReadStatus;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatReadUpdatedPayload;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.domain.user.dao.UserRepository;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatReadUpdateServiceTest {

    @Mock
    private ChatSocketAccessService accessService;

    @Mock
    private ChatReadStatusRepository chatReadStatusRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatSocketEventPublisher eventPublisher;

    private ChatReadUpdateService readUpdateService;

    @BeforeEach
    void setUp() {
        ChatReadStatusService readStatusService = new ChatReadStatusService(
                userRepository, chatMessageRepository, chatReadStatusRepository);
        readUpdateService = new ChatReadUpdateService(accessService, readStatusService, eventPublisher);
    }

    @Test
    void updatesExistingReadStatusAndPublishesSameMessageId() {
        Principal principal = () -> "test@example.com";
        User user = UserFixture.createUserWithId(21L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(PatternFixture.createPatternWithId(100L), 10L);
        ChatRoomStatus status = ChatRoomStatus.builder().user(user).room(room).nickname("민트 메리노").build();
        ChatReadStatus readStatus = ChatReadStatus.builder()
                .user(user).room(room).lastReadMessageId(1L).readAt(LocalDateTime.of(2026, 3, 9, 10, 0)).build();
        when(accessService.resolveUser(principal, 10L, null)).thenReturn(Optional.of(user));
        when(accessService.resolveRoom(10L, null)).thenReturn(Optional.of(room));
        when(accessService.resolveRoomStatus(21L, 10L, null)).thenReturn(Optional.of(status));
        when(chatMessageRepository.findByIdAndRoom_Id(53L, 10L))
                .thenReturn(Optional.of(ChatMessage.builder().room(room).user(user).text("메시지").build()));
        when(userRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(user));
        when(chatReadStatusRepository.findByRoom_IdAndUser_Id(10L, 21L)).thenReturn(Optional.of(readStatus));

        readUpdateService.update(principal, new ChatReadUpdateRequest(10L, 53L));

        assertThat(readStatus.getLastReadMessageId()).isEqualTo(53L);
        ArgumentCaptor<ChatReadUpdatedPayload> payloadCaptor = ArgumentCaptor.forClass(ChatReadUpdatedPayload.class);
        verify(eventPublisher).sendReadUpdated(org.mockito.ArgumentMatchers.eq(10L), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().userId()).isEqualTo(21L);
        assertThat(payloadCaptor.getValue().lastReadMessageId()).isEqualTo(53L);
        assertThat(payloadCaptor.getValue().readAt()).isEqualTo(readStatus.getReadAt());
    }
}
