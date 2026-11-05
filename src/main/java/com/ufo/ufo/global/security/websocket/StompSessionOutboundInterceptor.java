package com.ufo.ufo.global.security.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

@RequiredArgsConstructor
public class StompSessionOutboundInterceptor implements ExecutorChannelInterceptor {

    private final AuthenticatedWebSocketSessions sessions;

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        // 업무 트랜잭션의 DB 연결을 점유한 채 추가 연결을 기다리지 않도록 송신 스레드에서 검사한다.
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE) {
            try {
                sessions.requireActive(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
            } catch (MessageDeliveryException failure) {
                return null;
            }
        }
        return message;
    }
}
