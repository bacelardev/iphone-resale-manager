package io.github.bacelardev.iphoneresale.application.dto.auth;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt, AuthUserData user) {
}
