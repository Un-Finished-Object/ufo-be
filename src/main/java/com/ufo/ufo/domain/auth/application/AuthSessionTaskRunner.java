package com.ufo.ufo.domain.auth.application;

import com.ufo.ufo.global.security.websocket.AuthenticatedWebSocketSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class AuthSessionTaskRunner {

    private final AuthSessionCleanup cleanup;
    private final AuthenticatedWebSocketSessions sockets;

    @Scheduled(fixedDelayString = "${app.auth.sessions.cleanup-delay-ms:3600000}")
    public void deleteExpired() {
        cleanup.deleteExpired();
    }

    @Scheduled(fixedDelayString = "${app.auth.sessions.socket-check-delay-ms:30000}")
    public void closeInvalidSessions() {
        sockets.closeInvalidSessions();
    }
}
