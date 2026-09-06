package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.domain.enums.UserRole;

import java.util.UUID;

public record AuthenticatedUserPrincipal(UUID userId, String username, UserRole role) {
}
