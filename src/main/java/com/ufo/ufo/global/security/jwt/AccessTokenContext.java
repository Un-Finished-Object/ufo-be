package com.ufo.ufo.global.security.jwt;

import java.time.Instant;

public record AccessTokenContext(String email, String sessionId, String tokenHash, Instant expiresAt) {
}
