package io.github.bacelardev.iphoneresale.web.dto.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthUserData;
import io.github.bacelardev.iphoneresale.domain.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String username,
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static UserResponse from(AuthUserData user) {
        return new UserResponse(
                user.id(),
                user.name(),
                user.username(),
                user.role(),
                user.active(),
                user.createdAt(),
                user.updatedAt(),
                user.version()
        );
    }
}
