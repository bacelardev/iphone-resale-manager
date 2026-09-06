package io.github.bacelardev.iphoneresale.application.dto.auth;

import io.github.bacelardev.iphoneresale.domain.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AuthUserData(
        UUID id,
        String name,
        String username,
        String passwordHash,
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
