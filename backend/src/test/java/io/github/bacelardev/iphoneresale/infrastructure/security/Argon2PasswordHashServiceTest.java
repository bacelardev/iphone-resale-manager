package io.github.bacelardev.iphoneresale.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2PasswordHashServiceTest {

    private final Argon2PasswordHashService service = new Argon2PasswordHashService();

    @Test
    void usesRandomSaltAndTheApprovedArgon2idEncodingWithinTheExistingColumn() {
        String password = UUID.randomUUID().toString();
        String first = service.encode(password);
        String second = service.encode(password);

        assertThat(first).isNotEqualTo(second);
        for (String encoded : new String[]{first, second}) {
            assertThat(encoded).matches(
                    "^\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}$");
            assertThat(encoded).hasSize(97);
            assertThat(encoded.length()).isLessThanOrEqualTo(255);
            assertThat(service.matches(password, encoded)).isTrue();
            assertThat(service.matches(password + "x", encoded)).isFalse();
        }
    }

    @ParameterizedTest(name = "supported password fixture {index}")
    @MethodSource("supportedPasswords")
    void preservesTheEntirePasswordIncludingBytesBeyond72(String password) {
        String encoded = service.encode(password);
        assertThat(service.matches(password, encoded)).isTrue();
        String differentLastCharacter = password.substring(0,
                password.offsetByCodePoints(password.length(), -1)) + "!";
        assertThat(service.matches(differentLastCharacter, encoded)).isFalse();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            assertThat(service.matches(password.substring(0, 36), encoded)).isFalse();
        }
    }

    static Stream<String> supportedPasswords() {
        return Stream.of("a".repeat(12), "b".repeat(127), "c".repeat(128),
                "á漢ç€".repeat(32), "🔐".repeat(60), "dummy-authentication-value");
    }
}
