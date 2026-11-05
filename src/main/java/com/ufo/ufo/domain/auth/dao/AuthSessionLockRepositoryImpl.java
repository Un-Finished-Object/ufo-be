package com.ufo.ufo.domain.auth.dao;

import com.ufo.ufo.domain.auth.domain.AuthSession;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class AuthSessionLockRepositoryImpl implements AuthSessionLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Optional<AuthSession> findByIdForUpdate(String sessionId) {
        AuthSession session = entityManager.find(AuthSession.class, sessionId);
        if (session == null) {
            return Optional.empty();
        }
        LockModeType lock = entityManager.getLockMode(session);
        if (lock != LockModeType.PESSIMISTIC_WRITE && lock != LockModeType.PESSIMISTIC_FORCE_INCREMENT) {
            try {
                // 대기 중 다른 트랜잭션이 커밋한 무효화 상태까지 다시 읽는다.
                entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
            } catch (EntityNotFoundException missing) {
                return Optional.empty();
            }
        }
        return Optional.of(session);
    }
}
