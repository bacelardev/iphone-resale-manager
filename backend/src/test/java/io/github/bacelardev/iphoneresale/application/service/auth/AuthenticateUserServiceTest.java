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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final Duration ACCOUNT_WINDOW = Duration.ofMinutes(15);
    private static final int ACCOUNT_LIMIT = 3;

    @Mock private AuthUserStore userStore;
    @Mock private AuthSessionStore sessionStore;
    @Mock private PasswordHashService passwordHashService;
    @Mock private AccessTokenGenerator tokenGenerator;
    @Mock private AccessTokenHasher tokenHasher;

    private MutableClock limiterClock;
    private AuthenticateUserService service;

    @BeforeEach
    void setUp() {
        when(passwordHashService.encode("dummy-authentication-value")).thenReturn("dummy-hash");
        limiterClock = new MutableClock(NOW);
        service = new AuthenticateUserService(
                userStore,
                sessionStore,
                passwordHashService,
                tokenGenerator,
                tokenHasher,
                new AuthProperties(Duration.ofHours(12), 5, Duration.ofMinutes(1), 10_000,
                        ACCOUNT_LIMIT, ACCOUNT_WINDOW, 10_000, 0),
                new InMemoryLoginRateLimiter(ACCOUNT_LIMIT, ACCOUNT_WINDOW, 100, limiterClock),
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
        UUID userId = activeUser("socio");
        when(passwordHashService.matches("correct-password", "stored-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");

        IssuedAccessToken issued = service.authenticate("SOCIO", "correct-password");

        assertThat(issued.value()).isEqualTo("raw-token");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        verify(sessionStore).create(
                userId, "token-hash", NOW, NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void blocksTheNormalizedAccountAfterTheLimitWithoutCheckingThePassword() {
        activeUser("victim");
        when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);

        for (String variant : new String[]{"victim", " Victim ", "VICTIM"}) {
            assertThatThrownBy(() -> service.authenticate(variant, "wrong-password"))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);
        }
        assertThatThrownBy(() -> service.authenticate("vIcTiM", "wrong-password"))
                .isInstanceOfSatisfying(LoginRateLimitedException.class, blocked ->
                        assertThat(blocked.retryAfterSeconds()).isBetween(1L, ACCOUNT_WINDOW.toSeconds()));

        verify(passwordHashService, times(ACCOUNT_LIMIT)).matches(anyString(), anyString());
    }

    @Test
    void blockedAccountRejectsEvenTheCorrectPasswordSoTheLimitIsNotAnOracle() {
        activeUser("victim");
        lenient().when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);
        exhaust("victim");

        assertThatThrownBy(() -> service.authenticate("victim", "correct-password"))
                .isExactlyInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void throttlesUnknownAccountsExactlyLikeExistingOnes() {
        when(userStore.findByUsername("ghost")).thenReturn(Optional.empty());
        when(passwordHashService.matches("wrong-password", "dummy-hash")).thenReturn(false);
        exhaust("ghost");

        assertThatThrownBy(() -> service.authenticate("ghost", "wrong-password"))
                .isExactlyInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void keepsSeparateCountersPerAccount() {
        activeUser("victim");
        activeUser("other");
        when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);
        exhaust("victim");

        assertThatThrownBy(() -> service.authenticate("other", "wrong-password"))
                .isExactlyInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> service.authenticate("victim", "wrong-password"))
                .isExactlyInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void successfulLoginResetsOnlyItsOwnAccountCounter() {
        activeUser("socio");
        when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);
        when(passwordHashService.matches("correct-password", "stored-hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("token-hash");

        for (int attempt = 1; attempt < ACCOUNT_LIMIT; attempt++) {
            assertThatThrownBy(() -> service.authenticate("socio", "wrong-password"))
                    .isExactlyInstanceOf(AuthenticationFailedException.class);
        }
        service.authenticate("socio", "correct-password");

        exhaust("socio");
        assertThatThrownBy(() -> service.authenticate("socio", "wrong-password"))
                .isExactlyInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void releasesTheAccountOnceTheWindowExpires() {
        activeUser("victim");
        when(passwordHashService.matches("wrong-password", "stored-hash")).thenReturn(false);
        exhaust("victim");
        assertThatThrownBy(() -> service.authenticate("victim", "wrong-password"))
                .isExactlyInstanceOf(LoginRateLimitedException.class);

        limiterClock.advance(ACCOUNT_WINDOW);

        assertThatThrownBy(() -> service.authenticate("victim", "wrong-password"))
                .isExactlyInstanceOf(AuthenticationFailedException.class);
    }

    private void exhaust(String username) {
        for (int attempt = 0; attempt < ACCOUNT_LIMIT; attempt++) {
            assertThatThrownBy(() -> service.authenticate(username, "wrong-password"))
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

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
