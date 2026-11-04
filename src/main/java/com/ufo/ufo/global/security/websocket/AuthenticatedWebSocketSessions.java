package com.ufo.ufo.global.security.websocket;

import com.ufo.ufo.domain.auth.application.AuthSessionService;
import com.ufo.ufo.global.observability.ErrorLogSupport;
import com.ufo.ufo.global.security.jwt.AccessTokenContext;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticatedWebSocketSessions {

    private final AuthSessionService authSessionService;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public void opened(WebSocketSession socket) {
        connections.put(socket.getId(), new Connection(socket));
    }

    public void closed(String sessionId) {
        connections.remove(sessionId);
    }

    public void authenticate(String sessionId, AccessTokenContext context) {
        Connection connection = sessionId == null ? null : connections.get(sessionId);
        if (connection == null) {
            throw new MessageDeliveryException("인증할 WebSocket 연결을 찾을 수 없습니다.");
        }
        connection.context = context;
        requireActive(sessionId);
    }

    public void requireActive(String sessionId) {
        Connection connection = sessionId == null ? null : connections.get(sessionId);
        if (connection != null && connection.context != null) {
            try {
                if (authSessionService.isActive(connection.context)) {
                    return;
                }
            } catch (RuntimeException failure) {
                log.warn("WebSocket 인증 상태 조회 실패: {}", ErrorLogSupport.stackTrace(failure));
            }
        }
        if (connection != null) {
            close(connection);
        }
        throw new MessageDeliveryException("유효한 인증이 필요합니다.");
    }

    @Scheduled(fixedDelayString = "${app.auth.sessions.socket-check-delay-ms:30000}")
    public void closeInvalidSessions() {
        connections.forEach((id, connection) -> {
            if (connection.context != null) {
                try {
                    requireActive(id);
                } catch (MessageDeliveryException ignored) {
                    // 무효화한 연결은 requireActive에서 종료한다.
                }
            }
        });
    }

    private void close(Connection connection) {
        try {
            connection.socket.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException failure) {
            log.warn("WebSocket 연결 종료 실패: {}", ErrorLogSupport.stackTrace(failure));
        } finally {
            connections.remove(connection.socket.getId(), connection);
        }
    }

    private static final class Connection {
        private final WebSocketSession socket;
        private volatile AccessTokenContext context;

        private Connection(WebSocketSession socket) {
            this.socket = socket;
        }
    }
}
