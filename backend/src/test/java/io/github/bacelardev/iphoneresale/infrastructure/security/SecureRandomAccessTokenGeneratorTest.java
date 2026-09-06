package io.github.bacelardev.iphoneresale.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomAccessTokenGeneratorTest {

    @Test
    void generatesUnique256BitUrlSafeTokensWithPublicPrefix() {
        SecureRandomAccessTokenGenerator generator = new SecureRandomAccessTokenGenerator();
        Set<String> tokens = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            String token = generator.generate();
            assertThat(token).matches("^irs_[A-Za-z0-9_-]{43}$");
            tokens.add(token);
        }

        assertThat(tokens).hasSize(1_000);
    }
}
