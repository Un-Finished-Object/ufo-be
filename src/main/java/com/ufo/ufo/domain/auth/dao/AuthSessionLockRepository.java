package com.ufo.ufo.domain.auth.dao;

import com.ufo.ufo.domain.auth.domain.AuthSession;
import java.util.Optional;

public interface AuthSessionLockRepository {
    Optional<AuthSession> findByIdForUpdate(String sessionId);
}
