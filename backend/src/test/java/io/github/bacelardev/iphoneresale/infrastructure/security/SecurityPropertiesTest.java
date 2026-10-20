package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.config.properties.AuthProperties;
import io.github.bacelardev.iphoneresale.config.properties.BootstrapProperties;
import io.github.bacelardev.iphoneresale.config.properties.CorsProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPropertiesTest {

    @Test
    void rejectsCorsWildcard() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void rejectsInfiniteOrNonPositiveTokenLifetime() {
        assertThatThrownBy(() -> auth(Duration.ZERO, 5, MINUTES_15, 100, MINUTES_15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token-ttl");
    }

    @Test
    void rejectsNonPositiveAccountLoginWindows() {
        assertThatThrownBy(() -> auth(Duration.ofHours(12), 5, Duration.ZERO, 100, MINUTES_15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login-account-client-window");
        assertThatThrownBy(() -> auth(Duration.ofHours(12), 5, MINUTES_15, 100, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login-account-window");
    }

    @Test
    void acceptsTheDocumentedDefaults() {
        AuthProperties defaults = auth(Duration.ofHours(12), 5, MINUTES_15, 100, MINUTES_15);

        assertThat(defaults.loginAccountClientMaxFailures()).isEqualTo(5);
        assertThat(defaults.loginAccountMaxFailures()).isEqualTo(100);
    }

    @Test
    void requiresTheAccountLimitToExceedWhatOneClientCanContribute() {
        assertThatThrownBy(() -> auth(Duration.ofHours(12), 5, MINUTES_15, 10, MINUTES_15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login-account-max-failures");
        assertThat(auth(Duration.ofHours(12), 5, MINUTES_15, 11, MINUTES_15).loginAccountMaxFailures())
                .isEqualTo(11);
    }

    @Test
    void requiresTheAccountAndClientWindowToCoverTheAccountWindow() {
        assertThatThrownBy(() -> auth(Duration.ofHours(12), 5, Duration.ofMinutes(5), 100, MINUTES_15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login-account-client-window");
        assertThat(auth(Duration.ofHours(12), 5, Duration.ofMinutes(30), 100, MINUTES_15)).isNotNull();
    }

    private static final Duration MINUTES_15 = Duration.ofMinutes(15);

    private static AuthProperties auth(Duration tokenTtl, int clientFailures, Duration clientWindow,
                                       int accountFailures, Duration accountWindow) {
        return new AuthProperties(tokenTtl, 5, Duration.ofMinutes(1), 10_000,
                clientFailures, clientWindow, accountFailures, accountWindow, 10_000, 0);
    }

    @Test
    void bootstrapPropertiesNeverRenderSecrets() {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setEnabled(true);
        properties.setName("Sensitive Name");
        properties.setUsername("sensitive.user");
        properties.setPassword("sensitive-password");

        assertThat(properties.toString())
                .doesNotContain("Sensitive Name", "sensitive.user", "sensitive-password")
                .contains("[redacted]");
    }
}
