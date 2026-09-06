package io.github.bacelardev.iphoneresale.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptPasswordHashServiceTest {

    private final BCryptPasswordHashService service = new BCryptPasswordHashService();

    @Test
    void usesSaltAndCostTwelveAndRejectsWrongPassword() {
        String password = UUID.randomUUID().toString();
        String first = service.encode(password);
        String second = service.encode(password);

        assertThat(first).startsWith("$2a$12$").isNotEqualTo(second);
        assertThat(service.matches(password, first)).isTrue();
        assertThat(service.matches(UUID.randomUUID().toString(), first)).isFalse();
    }

    @Test
    void documentsTheOutstandingDirectBcryptByteLimit() {
        // Characterization of the E-05 gap; this is not acceptance of the 128-character requirement.
        assertThatThrownBy(() -> service.encode("x".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.encode("é".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
