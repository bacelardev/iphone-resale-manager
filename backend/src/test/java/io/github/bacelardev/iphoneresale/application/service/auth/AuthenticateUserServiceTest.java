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
import io.github.bacelardev.iphoneresale.infrastructure.security.InMemoryLoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final int CLIENT_LIMIT = 5;
    private static final int ACCOUNT_LIMIT = 11;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String CLIENT = "198.51.100.10";

    @Mock private AuthUserStore userStore;
    @Mock private AuthSessionStore sessionStore;
    @Mock private PasswordHashService passwordHashService;
    @Mock private AccessTokenGenerator tokenGenerator;
    @Mock private AccessTokenHasher tokenHasher;

    private AuthenticateUserService service;

    @BeforeEach
    void setUp() {
        when(passwordHashService.encode("dummy-authentication-value")).thenReturn("dummy-hash");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AuthenticateUserService(
                userStore,
                sessionStore,
                passwordHashService,
                tokenGenerator,
                tokenHasher,
                new AuthProperties(Duration.ofHours(12), 5, Duration.ofMinutes(1), 10_000,
                        CLIENT_LIMIT, WINDOW, ACCOUNT_LIMIT, WINDOW, 10_000, 0),
                new LoginThrottle(
                        new InMemoryLoginRateLimiter(CLIENT_LIMIT, WINDOW, 100, clock),
                        new InMemoryLoginRateLimiter(ACCOUNT_LIMIT, WINDOW, 100, clock)),
                clock
        );
    }

    @Test
    void usesDummyHashForUnknownUsernameAndReturnsGenericFailure() {
        when(userStore.findByUsername("missing")).thenReturn(Optional.empty());
        when(passwordHashService.matches("wrong-password", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("MISSING", "wrong-password", CLIENT))
                .isExactlyInstanceOf(AuthenticationFailedException.class);

        verify(passwordHashService).matches("wrong-password", "dummy-hash");
    }

    @Test
    void issuesAndPersistsOnlyTheHashForAnActiveUser() {
        UUID userId = activeUser("socio");
        when(passwordHashService.matches("correct-password", "stored-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");

        IssuedAccessToken issued = service.authenticate("SOCIO", "correct-password", CLIENT);

        assertThat(issued.value()).isEqualTo("raw-token");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        verify(sessionStore).create(
                userId, "token-hash", NOW, NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void usernameVariantsShareOneAccountAndClientCounterAndBlockedAttemptsSkipThePassword() {
        activeUser("victim");
        when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);

        for (String variant : new String[]{"victim", " Victim ", "VICTIM", "vIcTiM ", "  victim"}) {
            assertThatThrownBy(() -> service.authenticate(variant, "wrong-password", CLIENT))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);
        }
        assertThatThrownBy(() -> service.authenticate("Victim", "wrong-password", CLIENT))
                .isInstanceOfSatisfying(LoginRateLimitedException.class, blocked ->
                        assertThat(blocked.retryAfterSeconds()).isBetween(1L, WINDOW.toSeconds()));

        verify(passwordHashService, times(CLIENT_LIMIT)).matches(anyString(), anyString());
    }

    @Test
    void blockedCombinationRejectsTheCorrectPasswordButAnotherClientStillSignsIn() {
        activeUser("victim");
        when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);
        when(passwordHashService.matches("correct-password", "stored-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");
        exhaust("victim", CLIENT);

        assertThatThrownBy(() -> service.authenticate("victim", "correct-password", CLIENT))
                .isExactlyInstanceOf(LoginRateLimitedException.class);
        assertThat(service.authenticate("victim", "correct-password", "203.0.113.20").value())
                .isEqualTo("raw-token");
    }

    @Test
    void unknownAndExistingAccountsProduceTheSameSequenceOfOutcomes() {
        activeUser("existing");
        lenient().when(userStore.findByUsername("ghost")).thenReturn(Optional.empty());
        lenient().when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);
        lenient().when(passwordHashService.matches("wrong-password", "dummy-hash")).thenReturn(false);

        assertThat(outcomes("existing")).isEqualTo(outcomes("ghost"));
    }

    @Test
    void unexpectedErrorsDoNotCountAsFailures() {
        when(userStore.findByUsername("victim")).thenThrow(new IllegalStateException("database down"));

        for (int attempt = 0; attempt < 3 * ACCOUNT_LIMIT; attempt++) {
            assertThatThrownBy(() -> service.authenticate("victim", "wrong-password", CLIENT))
                    .isExactlyInstanceOf(IllegalStateException.class);
        }
        verify(passwordHashService, never()).matches(anyString(), anyString());
    }

    private List<String> outcomes(String username) {
        List<String> outcomes = new ArrayList<>();
        for (int client = 1; client <= 3; client++) {
            for (int attempt = 0; attempt <= CLIENT_LIMIT; attempt++) {
                outcomes.add(outcome(username, "192.0.2." + client));
            }
        }
        return outcomes;
    }

    private String outcome(String username, String client) {
        try {
            service.authenticate(username, "wrong-password", client);
            return "authenticated";
        } catch (AuthenticationFailedException failed) {
            return "401";
        } catch (LoginRateLimitedException limited) {
            return "429:" + (limited.retryAfterSeconds() > 0);
        }
    }

    private void exhaust(String username, String client) {
        for (int attempt = 0; attempt < CLIENT_LIMIT; attempt++) {
            assertThatThrownBy(() -> service.authenticate(username, "wrong-password", client))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);
        }
    }

    private UUID activeUser(String username) {
        UUID userId = UUID.randomUUID();
        lenient().when(userStore.findByUsername(username)).thenReturn(Optional.of(new AuthUserData(
                userId, "Sócio", username, "stored-hash", UserRole.SOCIO, true,
                NOW.minusSeconds(60), NOW.minusSeconds(60), 0
        )));
        return userId;
    }
}
