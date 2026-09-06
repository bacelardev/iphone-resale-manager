package io.github.bacelardev.iphoneresale.application.port.security;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthSessionData;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionStore {

    void create(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt);

    Optional<AuthSessionData> findActiveByTokenHash(String tokenHash, Instant now);

    int revokeByTokenHash(String tokenHash, Instant revokedAt);

    int revokeAllForUser(UUID userId, Instant revokedAt);
}
