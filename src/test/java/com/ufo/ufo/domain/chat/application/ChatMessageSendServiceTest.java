package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.dao.ChatMessageRepository;
import com.ufo.ufo.domain.chat.domain.ChatMessage;
import com.ufo.ufo.domain.chat.domain.ChatRoom;
import com.ufo.ufo.domain.chat.domain.ChatRoomStatus;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.response.ChatMessageCreatedPayload;
import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.support.fixture.ChatRoomFixture;
import com.ufo.ufo.support.fixture.PatternFixture;
import com.ufo.ufo.support.fixture.UserFixture;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageSendServiceTest {

    @Mock
    private ChatSocketAccessService accessService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatSocketEventPublisher eventPublisher;

    @InjectMocks
    private ChatMessageSendService messageSendService;

    @Test
    void trimsAndSavesMessageBeforePublishingPayload() {
        Principal principal = () -> "test@example.com";
        User user = UserFixture.createUserWithId(21L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(PatternFixture.createPatternWithId(100L), 10L);
        ChatRoomStatus status = ChatRoomStatus.builder().user(user).room(room).nickname("민트 메리노").build();
        when(accessService.resolveUser(principal, 10L, "temp-1")).thenReturn(Optional.of(user));
        when(accessService.resolveRoom(10L, "temp-1")).thenReturn(Optional.of(room));
        when(accessService.resolveRoomStatus(21L, 10L, "temp-1")).thenReturn(Optional.of(status));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        messageSendService.send(principal, new ChatMessageSendRequest(10L, " 안녕하세요 ", "temp-1", false, null));

        ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getText()).isEqualTo("안녕하세요");
        ArgumentCaptor<ChatMessageCreatedPayload> payloadCaptor = ArgumentCaptor.forClass(ChatMessageCreatedPayload.class);
        verify(eventPublisher).sendMessageCreated(org.mockito.ArgumentMatchers.eq(10L), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().senderName()).isEqualTo("민트 메리노");
        assertThat(payloadCaptor.getValue().text()).isEqualTo("안녕하세요");
        assertThat(payloadCaptor.getValue().clientMessageId()).isEqualTo("temp-1");
    }

    @Test
    void blankMessageSendsErrorWithoutSaving() {
        Principal principal = () -> "test@example.com";
        User user = UserFixture.createUserWithId(21L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(PatternFixture.createPatternWithId(100L), 10L);
        ChatRoomStatus status = ChatRoomStatus.builder().user(user).room(room).nickname("민트 메리노").build();
        when(accessService.resolveUser(principal, 10L, "temp-2")).thenReturn(Optional.of(user));
        when(accessService.resolveRoom(10L, "temp-2")).thenReturn(Optional.of(room));
        when(accessService.resolveRoomStatus(21L, 10L, "temp-2")).thenReturn(Optional.of(status));

        messageSendService.send(principal, new ChatMessageSendRequest(10L, "  ", "temp-2", false, null));

        verify(eventPublisher).sendError(10L, "INVALID_MESSAGE_TEXT", "메시지 내용은 비어 있을 수 없습니다.", "temp-2");
        verifyNoInteractions(chatMessageRepository);
    }
}
