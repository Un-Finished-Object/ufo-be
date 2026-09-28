package com.ufo.ufo.global.security.websocket;

import com.ufo.ufo.domain.chat.application.ChatSubscriptionAccessService;
import com.ufo.ufo.global.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompJwtAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_SUBSCRIPTION = Pattern.compile("^/sub/chat/rooms/([1-9][0-9]*)$");
    private static final Set<String> PUBLISH_DESTINATIONS = Set.of("/pub/chat/message", "/pub/chat/read");

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatSubscriptionAccessService chatSubscriptionAccessService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            throw new MessageDeliveryException("유효한 STOMP 프레임이 필요합니다.");
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticate(accessor);
            return message;
        }

        if (command == StompCommand.DISCONNECT || accessor.isHeartbeat()) {
            return message;
        }

        Authentication authentication = requireAuthentication(accessor);
        if (command == StompCommand.SUBSCRIBE) {
            Long roomId = subscriptionRoomId(accessor.getDestination());
            if (!chatSubscriptionAccessService.canSubscribe(authentication, roomId)) {
                throw new MessageDeliveryException("구독할 수 없는 채팅방입니다.");
            }
        } else if (command == StompCommand.SEND) {
            String destination = accessor.getDestination();
            if (destination == null || !PUBLISH_DESTINATIONS.contains(destination)) {
                throw new MessageDeliveryException("허용되지 않은 메시지 전송 경로입니다.");
            }
        } else if (command != StompCommand.UNSUBSCRIBE) {
            throw new MessageDeliveryException("지원하지 않는 STOMP 명령입니다.");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        try {
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                throw new MessageDeliveryException("유효한 인증 토큰이 필요합니다.");
            }
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            if (!isAuthenticated(authentication)) {
                throw new MessageDeliveryException("인증 사용자 정보를 확인할 수 없습니다.");
            }
            accessor.setUser(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new MessageDeliveryException("유효한 인증 토큰이 필요합니다.");
        }
    }

    private Authentication requireAuthentication(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication) || !isAuthenticated(authentication)) {
            throw new MessageDeliveryException("인증 사용자 정보를 확인할 수 없습니다.");
        }
        return authentication;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null && !authentication.getName().isBlank();
    }

    private Long subscriptionRoomId(String destination) {
        Matcher matcher = ROOM_SUBSCRIPTION.matcher(destination == null ? "" : destination);
        if (!matcher.matches()) {
            throw new MessageDeliveryException("허용되지 않은 채팅방 구독 경로입니다.");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new MessageDeliveryException("허용되지 않은 채팅방 구독 경로입니다.");
        }
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
