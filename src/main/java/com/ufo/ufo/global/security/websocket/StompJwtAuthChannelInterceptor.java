package com.ufo.ufo.global.security.websocket;

import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompJwtAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = resolveToken(accessor);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return message;
        }

        Authentication authentication = jwtTokenProvider.getAuthentication(token);
        accessor.setUser(authentication);
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String bearerToken = getFirstNativeHeader(accessor);
        if (bearerToken == null || !bearerToken.startsWith(JwtTokenProvider.BEARER_PREFIX)) {
            return null;
        }
        return bearerToken.substring(JwtTokenProvider.BEARER_PREFIX.length());
    }

    private String getFirstNativeHeader(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader(HttpHeaders.AUTHORIZATION);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
