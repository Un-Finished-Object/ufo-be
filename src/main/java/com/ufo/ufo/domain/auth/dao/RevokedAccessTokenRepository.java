package com.ufo.ufo.domain.auth.dao;

import com.ufo.ufo.domain.auth.domain.RevokedAccessToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {

    @Modifying
    @Query("delete from RevokedAccessToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
