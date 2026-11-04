package com.ufo.ufo.global.security.jwt;

import java.time.Instant;

public record TokenClaims(String email, String role, String sessionId, Instant expiresAt) {
}
