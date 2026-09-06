package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthUserData;
import io.github.bacelardev.iphoneresale.application.dto.auth.IssuedAccessToken;
import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenGenerator;
import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenHasher;
import io.github.bacelardev.iphoneresale.application.port.security.AuthSessionStore;
import io.github.bacelardev.iphoneresale.application.port.security.AuthUserStore;
import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import io.github.bacelardev.iphoneresale.config.properties.AuthProperties;
import io.github.bacelardev.iphoneresale.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");

    @Mock private AuthUserStore userStore;
    @Mock private AuthSessionStore sessionStore;
    @Mock private PasswordHashService passwordHashService;
    @Mock private AccessTokenGenerator tokenGenerator;
    @Mock private AccessTokenHasher tokenHasher;

    private AuthenticateUserService service;

    @BeforeEach
    void setUp() {
        when(passwordHashService.encode("dummy-authentication-value")).thenReturn("dummy-hash");
        service = new AuthenticateUserService(
                userStore,
                sessionStore,
                passwordHashService,
                tokenGenerator,
                tokenHasher,
                new AuthProperties(Duration.ofHours(12), 5, Duration.ofMinutes(1), 10_000),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void usesDummyHashForUnknownUsernameAndReturnsGenericFailure() {
        when(userStore.findByUsername("missing")).thenReturn(Optional.empty());
        when(passwordHashService.matches("wrong-password", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("MISSING", "wrong-password"))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(passwordHashService).matches("wrong-password", "dummy-hash");
    }

    @Test
    void issuesAndPersistsOnlyTheHashForAnActiveUser() {
        UUID userId = UUID.randomUUID();
        AuthUserData user = new AuthUserData(
                userId, "Sócio", "socio", "stored-hash", UserRole.SOCIO, true,
                NOW.minusSeconds(60), NOW.minusSeconds(60), 0
        );
        when(userStore.findByUsername("socio")).thenReturn(Optional.of(user));
        when(passwordHashService.matches("correct-password", "stored-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");

        IssuedAccessToken issued = service.authenticate("SOCIO", "correct-password");

        assertThat(issued.value()).isEqualTo("raw-token");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        verify(sessionStore).create(
                userId, "token-hash", NOW, NOW.plus(Duration.ofHours(12)));
    }
}
