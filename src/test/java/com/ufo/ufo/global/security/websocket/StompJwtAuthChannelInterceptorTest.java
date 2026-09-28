package com.ufo.ufo.global.security.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ufo.ufo.domain.chat.application.ChatSubscriptionAccessService;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.security.SignatureException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("STOMP JWT 인증 인터셉터 테스트")
class StompJwtAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ChatSubscriptionAccessService chatSubscriptionAccessService;

    @InjectMocks
    private StompJwtAuthChannelInterceptor interceptor;

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"CONNECT", "STOMP"})
    @DisplayName("CONNECT 또는 STOMP 프레임의 유효한 토큰으로 사용자 인증 정보를 설정해야 한다")
    void preSend_WithValidBearerToken_SetsUserPrincipal(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Authentication authentication = new UsernamePasswordAuthenticationToken("test@example.com", "", null);

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getAuthentication("valid-token")).thenReturn(authentication);

        Message<?> result = interceptor.preSend(message, null);
        StompHeaderAccessor wrapped = StompHeaderAccessor.wrap(result);

        assertThat(wrapped.getUser()).isEqualTo(authentication);
        assertThat(wrapped.getUser().getName()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("CONNECT 프레임의 토큰이 유효하지 않으면 연결을 거부해야 한다")
    void preSend_WithInvalidToken_RejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Bearer ", "Basic valid-token"})
    @DisplayName("CONNECT에 Bearer 토큰이 없으면 연결을 거부해야 한다")
    void preSend_WithoutBearerToken_RejectsConnection(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("JWT 서명 검증 예외가 발생해도 인증 실패로 연결을 거부해야 한다")
    void preSend_WithSignatureFailure_RejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer tampered-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        when(jwtTokenProvider.validateToken("tampered-token"))
                .thenThrow(new SignatureException("Invalid signature"));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"SEND", "SUBSCRIBE", "UNSUBSCRIBE"})
    @DisplayName("인증되지 않은 연결에서 보호된 명령을 보내면 거부해야 한다")
    void preSend_WithoutAuthenticatedUser_RejectsProtectedCommands(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination("/pub/chat/message");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("익명 인증 정보로 메시지 전송을 허용하지 않아야 한다")
    void preSend_WithAnonymousAuthentication_RejectsSend() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/pub/chat/message");
        accessor.setUser(new AnonymousAuthenticationToken("anonymous", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("인증된 사용자의 구독 해제는 허용해야 한다")
    void preSend_WithAuthenticatedUser_AllowsUnsubscribe() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken("test@example.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    @DisplayName("연결 해제와 heartbeat는 인증 실패 후에도 처리할 수 있어야 한다")
    void preSend_AllowsDisconnectAndHeartbeatForCleanup() {
        StompHeaderAccessor disconnect = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        disconnect.setLeaveMutable(true);
        Message<byte[]> disconnectMessage = MessageBuilder.createMessage(new byte[0], disconnect.getMessageHeaders());
        StompHeaderAccessor heartbeat = StompHeaderAccessor.createForHeartbeat();
        heartbeat.setLeaveMutable(true);
        Message<byte[]> heartbeatMessage = MessageBuilder.createMessage(new byte[0], heartbeat.getMessageHeaders());

        assertThat(interceptor.preSend(disconnectMessage, null)).isSameAs(disconnectMessage);
        assertThat(interceptor.preSend(heartbeatMessage, null)).isSameAs(heartbeatMessage);
    }
}
