package io.github.bacelardev.iphoneresale.application.dto.auth;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionData(UUID sessionId, AuthUserData user, Instant expiresAt) {
}
