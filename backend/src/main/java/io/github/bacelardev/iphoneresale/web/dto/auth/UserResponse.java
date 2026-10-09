package io.github.bacelardev.iphoneresale.web.dto.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthUserData;
import io.github.bacelardev.iphoneresale.domain.enums.UserRole;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;

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

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getUsername(), user.getRole(), user.isActive(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion() == null ? 0 : user.getVersion()
        );
    }
}
