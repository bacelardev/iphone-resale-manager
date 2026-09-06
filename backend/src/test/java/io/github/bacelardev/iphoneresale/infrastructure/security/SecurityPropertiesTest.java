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
        assertThatThrownBy(() -> new AuthProperties(
                Duration.ZERO, 5, Duration.ofMinutes(1), 10_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token-ttl");
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
