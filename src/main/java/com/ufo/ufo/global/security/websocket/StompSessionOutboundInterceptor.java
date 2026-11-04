package com.ufo.ufo.global.security.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;

@RequiredArgsConstructor
public class StompSessionOutboundInterceptor implements ChannelInterceptor {

    private final AuthenticatedWebSocketSessions sessions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
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
