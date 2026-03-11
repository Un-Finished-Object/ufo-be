package com.ufo.ufo.global.security.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
@DisplayName("STOMP JWT 인증 인터셉터 테스트")
class StompJwtAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private StompJwtAuthChannelInterceptor interceptor;

    @Test
    @DisplayName("CONNECT 프레임에 유효한 Authorization 헤더가 있으면 사용자 인증 정보를 설정해야 한다")
    void preSend_WithValidBearerToken_SetsUserPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
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
    @DisplayName("CONNECT 프레임에 토큰이 없거나 유효하지 않으면 사용자 인증 정보를 설정하지 않아야 한다")
    void preSend_WithInvalidToken_DoesNotSetUserPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        Message<?> result = interceptor.preSend(message, null);
        StompHeaderAccessor wrapped = StompHeaderAccessor.wrap(result);

        assertThat(wrapped.getUser()).isNull();
        verify(jwtTokenProvider, never()).getAuthentication("invalid-token");
    }
}
