package io.github.bacelardev.iphoneresale.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Authentication settings. Login is limited per resolved client ({@code login-max-attempts}), per
 * account and client ({@code login-account-client-*}) and per account ({@code login-account-*}).
 * The account limit must exceed twice the account-client limit and the account-client window must
 * not be shorter than the account window: one client then adds at most twice the account-client
 * limit to any account window and can never block an account for every other client on its own.
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotNull Duration tokenTtl,
        @Min(1) @Max(100) int loginMaxAttempts,
        @NotNull Duration loginWindow,
        @Min(100) @Max(100_000) int loginMaxTrackedClients,
        @Min(1) @Max(100) int loginAccountClientMaxFailures,
        @NotNull Duration loginAccountClientWindow,
        @Min(3) @Max(10_000) int loginAccountMaxFailures,
        @NotNull Duration loginAccountWindow,
        @Min(100) @Max(100_000) int loginMaxTrackedAccounts,
        @Min(0) @Max(10) int loginTrustedProxyHops
) {
    public AuthProperties {
        requirePositive(tokenTtl, "app.auth.token-ttl");
        requirePositive(loginWindow, "app.auth.login-window");
        requirePositive(loginAccountClientWindow, "app.auth.login-account-client-window");
        requirePositive(loginAccountWindow, "app.auth.login-account-window");
        if (loginAccountMaxFailures <= 2 * loginAccountClientMaxFailures) {
            throw new IllegalArgumentException("app.auth.login-account-max-failures must exceed twice "
                    + "app.auth.login-account-client-max-failures so one client cannot reach it alone");
        }
        if (loginAccountClientWindow != null && loginAccountWindow != null
                && loginAccountClientWindow.compareTo(loginAccountWindow) < 0) {
            throw new IllegalArgumentException("app.auth.login-account-client-window must not be shorter "
                    + "than app.auth.login-account-window");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
