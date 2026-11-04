package com.ufo.ufo.global.config;

import com.ufo.ufo.global.security.websocket.AuthenticatedWebSocketSessions;
import com.ufo.ufo.global.security.websocket.StompJwtAuthChannelInterceptor;
import com.ufo.ufo.global.security.websocket.StompSessionOutboundInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompJwtAuthChannelInterceptor stompJwtAuthChannelInterceptor;
    private final CorsProperties corsProperties;
    private final AuthenticatedWebSocketSessions authenticatedSessions;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtAuthChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompSessionOutboundInterceptor(authenticatedSessions));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                authenticatedSessions.opened(session);
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception failure) {
                    authenticatedSessions.closed(session.getId());
                    throw failure;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                try {
                    super.afterConnectionClosed(session, status);
                } finally {
                    authenticatedSessions.closed(session.getId());
                }
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(corsProperties.allowedOriginArray());
    }
}
