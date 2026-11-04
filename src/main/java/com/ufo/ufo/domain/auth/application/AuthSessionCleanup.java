package com.ufo.ufo.domain.auth.application;

import com.ufo.ufo.domain.auth.dao.AuthSessionRepository;
import com.ufo.ufo.domain.auth.dao.RevokedAccessTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuthSessionCleanup {

    private final AuthSessionRepository sessionRepository;
    private final RevokedAccessTokenRepository revokedTokenRepository;

    @Scheduled(fixedDelayString = "${app.auth.sessions.cleanup-delay-ms:3600000}")
    @Transactional
    public void deleteExpired() {
        Instant now = Instant.now();
        sessionRepository.deleteExpired(now);
        revokedTokenRepository.deleteExpired(now);
    }
}
