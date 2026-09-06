package io.github.bacelardev.iphoneresale.web.dto.auth;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {
}
