package com.ufo.ufo.domain.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import com.ufo.ufo.domain.chat.application.ChatMessageSendService;
import com.ufo.ufo.domain.chat.application.ChatReadUpdateService;
import com.ufo.ufo.domain.chat.application.ChatWebSocketService;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatMessageSendRequest;
import com.ufo.ufo.domain.chat.dto.websocket.request.ChatReadUpdateRequest;
import com.ufo.ufo.support.logging.LogCapture;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

@DisplayName("채팅 메시지 처리기의 오류 로그 테스트")
class ChatWebSocketFailureTest {

    @ParameterizedTest
    @MethodSource("failedRequests")
    @DisplayName("채팅 처리 실패는 원문 노출이나 추가 응답 없이 오류 로그를 한 번만 남겨야 한다")
    void handlesFailureWithoutLeakingExceptionOrSendingResponse(String destination, Object request) {
        var sender = mock(ChatMessageSendService.class);
        var reader = mock(ChatReadUpdateService.class);
        var failure = new IllegalStateException("token-secret", new RuntimeException("email@example.com"));
        failure.addSuppressed(new IllegalArgumentException("cookie-secret"));
        doThrow(failure).when(sender).send(any(), any());
        doThrow(failure).when(reader).update(any(), any());
        var service = new ChatWebSocketService(sender, reader);
        try (var handler = new MessageHandler(service);
                var serviceLogs = new LogCapture(ChatWebSocketService.class);
                var controllerLogs = new LogCapture(ChatWebSocketController.class);
                var frameworkLogs = new LogCapture(SimpAnnotationMethodMessageHandler.class)) {
            handler.send(destination, request);

            if (request instanceof ChatMessageSendRequest message) {
                verify(sender).send(handler.principal, message);
            } else {
                verify(reader).update(handler.principal, (ChatReadUpdateRequest) request);
            }
            var errors = Stream.of(serviceLogs, controllerLogs, frameworkLogs)
                    .flatMap(logs -> logs.events().stream()).filter(event -> event.getLevel() == Level.ERROR).toList();
            assertThat(errors).hasSize(1).allSatisfy(event -> {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage())
                        .doesNotContain("token-secret", "email@example.com", "cookie-secret", "private-message");
            });
            assertThat(serviceLogs.events().getFirst().getFormattedMessage()).contains("10");
            verifyNoInteractions(handler.outbound, handler.broker);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/pub/chat/message", "/pub/chat/read"})
    @DisplayName("요청 변환 실패는 예외 원문이나 응답을 노출하지 않고 오류 로그를 한 번 남겨야 한다")
    void recordsBindingFailureWithoutLeakingPayload(String destination) {
        var sender = mock(ChatMessageSendService.class);
        var reader = mock(ChatReadUpdateService.class);
        var service = new ChatWebSocketService(sender, reader);

        try (var handler = new MessageHandler(service);
                var serviceLogs = new LogCapture(ChatWebSocketService.class);
                var controllerLogs = new LogCapture(ChatWebSocketController.class);
                var frameworkLogs = new LogCapture(SimpAnnotationMethodMessageHandler.class)) {
            handler.send(destination, "private-message token-secret email@example.com cookie-secret");
            assertThat(serviceLogs.events()).isEmpty();
            assertThat(frameworkLogs.events()).isEmpty();
            assertThat(controllerLogs.events()).hasSize(1).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).contains("MessageConversionException")
                        .doesNotContain("private-message", "token-secret", "email@example.com", "cookie-secret");
            });
            verifyNoInteractions(sender, reader, handler.outbound, handler.broker);
        }
    }

    private static Stream<Arguments> failedRequests() {
        return Stream.of(
                Arguments.of("/pub/chat/message",
                        new ChatMessageSendRequest(10L, "private-message", "message-123", false, null)),
                Arguments.of("/pub/chat/read", new ChatReadUpdateRequest(10L, 20L))
        );
    }

    private static class MessageHandler implements AutoCloseable {

        private final GenericApplicationContext context = new GenericApplicationContext();
        private final ExecutorSubscribableChannel inbound = new ExecutorSubscribableChannel();
        private final MessageChannel outbound = mock(MessageChannel.class);
        private final MessageChannel broker = mock(MessageChannel.class);
        private final Principal principal = () -> "1";
        private final SimpAnnotationMethodMessageHandler handler;

        private MessageHandler(ChatWebSocketService service) {
            context.registerBean(ChatWebSocketController.class, () -> new ChatWebSocketController(service));
            context.refresh();
            handler = new SimpAnnotationMethodMessageHandler(inbound, outbound, new SimpMessagingTemplate(broker));
            handler.setApplicationContext(context);
            handler.setDestinationPrefixes(List.of("/pub"));
            handler.afterPropertiesSet();
            handler.start();
        }

        private void send(String destination, Object request) {
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setDestination(destination);
            headers.setSessionId("test-session");
            headers.setSessionAttributes(new HashMap<>());
            headers.setUser(principal);
            inbound.send(MessageBuilder.createMessage(request, headers.getMessageHeaders()));
        }

        @Override
        public void close() {
            handler.stop();
            context.close();
        }
    }
}
