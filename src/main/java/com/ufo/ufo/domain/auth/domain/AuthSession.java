package com.ufo.ufo.domain.auth.domain;

import com.ufo.ufo.domain.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_session_retention", columnList = "retain_until"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    @Id
    @Column(name = "session_id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant refreshExpiresAt;

    @Column(nullable = false)
    private Instant retainUntil;

    private Instant revokedAt;

    public AuthSession(String id, User user, Instant refreshExpiresAt) {
        this.id = id;
        this.user = user;
        this.refreshExpiresAt = refreshExpiresAt;
        this.retainUntil = refreshExpiresAt;
    }

    public boolean canRefresh(Instant now) {
        return revokedAt == null && now.isBefore(refreshExpiresAt);
    }

    public void retainUntil(Instant expiresAt) {
        if (expiresAt.isAfter(retainUntil)) {
            retainUntil = expiresAt;
        }
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
