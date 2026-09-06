package io.github.bacelardev.iphoneresale.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotNull Duration tokenTtl,
        @Min(1) @Max(100) int loginMaxAttempts,
        @NotNull Duration loginWindow,
        @Min(100) @Max(100_000) int loginMaxTrackedClients
) {
    public AuthProperties {
        if (tokenTtl != null && (tokenTtl.isZero() || tokenTtl.isNegative())) {
            throw new IllegalArgumentException("app.auth.token-ttl must be positive");
        }
        if (loginWindow != null && (loginWindow.isZero() || loginWindow.isNegative())) {
            throw new IllegalArgumentException("app.auth.login-window must be positive");
        }
    }
}
