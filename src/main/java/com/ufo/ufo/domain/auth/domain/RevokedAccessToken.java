package com.ufo.ufo.domain.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "revoked_access_tokens", indexes = @Index(name = "idx_revoked_access_expiry", columnList = "expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevokedAccessToken {

    @Id
    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    public RevokedAccessToken(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
