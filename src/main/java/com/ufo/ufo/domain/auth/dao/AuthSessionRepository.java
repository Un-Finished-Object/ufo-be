package com.ufo.ufo.domain.auth.dao;

import com.ufo.ufo.domain.auth.domain.AuthSession;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String>, AuthSessionLockRepository {

    boolean existsByIdAndUser_EmailAndRevokedAtIsNullAndRetainUntilAfter(String id, String email, Instant now);

    @Modifying
    @Query("delete from AuthSession s where s.retainUntil < :now")
    int deleteExpired(@Param("now") Instant now);
}
