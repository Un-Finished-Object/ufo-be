package com.ufo.ufo.domain.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 메시지 전송 서비스 테스트")
class ChatMessageSendServiceTest {

    @Mock
    private ChatSocketAccessService accessService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatSocketEventPublisher eventPublisher;

    @InjectMocks
    private ChatMessageSendService messageSendService;

    @ParameterizedTest
    @CsvSource({"a,999", "a,1000", "한,999", "한,1000", "😀,999", "😀,1000"})
    @DisplayName("공백을 제거한 메시지가 1,000자 이하면 저장할 메시지와 성공 이벤트에 반영해야 한다")
    void send_WithinLengthLimit_SavesNormalizedMessage(String character, int length) {
        Principal principal = prepareParticipant("length-valid");
        String text = character.repeat(length);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        messageSendService.send(principal,
                new ChatMessageSendRequest(10L, " \t" + text + "\n ", "length-valid", false, null));

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getText()).isEqualTo(text);
        ArgumentCaptor<ChatMessageCreatedPayload> payload = ArgumentCaptor.forClass(ChatMessageCreatedPayload.class);
        verify(eventPublisher).sendMessageCreated(eq(10L), payload.capture());
        assertThat(payload.getValue().text()).isEqualTo(text);
        assertThat(payload.getValue().clientMessageId()).isEqualTo("length-valid");
    }

    @ParameterizedTest
    @CsvSource({"a,1001", "한,1001", "😀,1001"})
    @DisplayName("1,000자를 넘는 메시지는 저장하지 않고 요청 식별자와 함께 오류를 반환해야 한다")
    void send_ExceedingLengthLimit_ReturnsErrorWithoutSaving(String character, int length) {
        Principal principal = prepareParticipant("length-invalid");

        messageSendService.send(principal,
                new ChatMessageSendRequest(10L, " " + character.repeat(length) + " ",
                        "length-invalid", true, 53L));

        verifyNoInteractions(chatMessageRepository);
        verify(eventPublisher).sendError(
                10L, "INVALID_MESSAGE_TEXT", "메시지는 1,000자까지 입력할 수 있습니다.", "length-invalid");
        verify(eventPublisher, never()).sendMessageCreated(any(), any());
    }

    @Test
    @DisplayName("메시지의 앞뒤 공백을 제거한 뒤 저장하고 성공 이벤트를 발행해야 한다")
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
    @DisplayName("공백뿐인 메시지는 저장하지 않고 오류를 반환해야 한다")
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

    private Principal prepareParticipant(String clientMessageId) {
        Principal principal = () -> "test@example.com";
        User user = UserFixture.createUserWithId(21L);
        ChatRoom room = ChatRoomFixture.createRoomWithId(PatternFixture.createPatternWithId(100L), 10L);
        ChatRoomStatus status = ChatRoomStatus.builder().user(user).room(room).nickname("민트 메리노").build();
        when(accessService.resolveUser(principal, 10L, clientMessageId)).thenReturn(Optional.of(user));
        when(accessService.resolveRoom(10L, clientMessageId)).thenReturn(Optional.of(room));
        when(accessService.resolveRoomStatus(21L, 10L, clientMessageId)).thenReturn(Optional.of(status));
        return principal;
    }
}
