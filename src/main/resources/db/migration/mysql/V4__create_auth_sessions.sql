CREATE TABLE auth_sessions (
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    refresh_expires_at TIMESTAMP(6) NOT NULL,
    retain_until TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (session_id),
    INDEX idx_auth_session_retention (retain_until),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE revoked_access_tokens (
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (token_hash),
    INDEX idx_revoked_access_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
