package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.infrastructure.security.InMemoryLoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real limiters with the production defaults: 5 per account and client, 100 per account. */
class LoginThrottleTest {

    private static final int CLIENT_LIMIT = 5;
    private static final int ACCOUNT_LIMIT = 100;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private MutableClock clock;
    private LoginThrottle throttle;
    private int nextClient;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-16T12:00:00Z"));
        throttle = new LoginThrottle(
                new InMemoryLoginRateLimiter(CLIENT_LIMIT, WINDOW, 10_000, clock),
                new InMemoryLoginRateLimiter(ACCOUNT_LIMIT, WINDOW, 10_000, clock));
    }

    @Test
    void fiveFailuresBlockOnlyThatAccountAndClientCombination() {
        String client = client();
        for (int attempt = 0; attempt < CLIENT_LIMIT; attempt++) {
            assertThat(fail("victim", client)).isTrue();
        }

        assertThatThrownBy(() -> throttle.admit("victim", client))
                .isInstanceOfSatisfying(LoginRateLimitedException.class, blocked ->
                        assertThat(blocked.retryAfterSeconds()).isBetween(1L, WINDOW.toSeconds()));
        assertThat(fail("victim", client())).isTrue();
        assertThat(fail("other", client)).isTrue();
    }

    @Test
    void requestsRejectedForTheAccountAndClientNeverReachTheAccountCounter() {
        String attacker = client();
        exhaustClient("victim", attacker);
        for (int rejected = 0; rejected < 500; rejected++) {
            assertThat(fail("victim", attacker)).isFalse();
        }

        // Only the attacker's 5 failures count: exactly 95 more fit before the account blocks.
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void accountStaysUsableFromAnotherClientWhileOneClientIsBlocked() {
        String attacker = client();
        exhaustClient("victim", attacker);

        try (LoginThrottle.Admission admission = throttle.admit("victim", client())) {
            admission.succeeded();
        }
    }

    @Test
    void oneClientCannotBlockTheAccountForOthersEvenOverManyWindows() {
        String attacker = client();
        String owner = client();
        for (int minute = 0; minute < 24 * 60; minute++) {
            for (int request = 0; request < 20; request++) {
                fail("victim", attacker);
            }
            if (minute % 10 == 5) {
                try (LoginThrottle.Admission admission = throttle.admit("victim", owner)) {
                    admission.succeeded();
                }
            }
            clock.advance(Duration.ofMinutes(1));
        }
    }

    @Test
    void distinctClientsAccumulateFailuresOnTheSameAccount() {
        for (int client = 0; client < ACCOUNT_LIMIT / CLIENT_LIMIT; client++) {
            exhaustClient("victim", client());
        }

        String fresh = client();
        assertThatThrownBy(() -> throttle.admit("victim", fresh))
                .isInstanceOfSatisfying(LoginRateLimitedException.class, blocked ->
                        assertThat(blocked.retryAfterSeconds()).isBetween(1L, WINDOW.toSeconds()));
    }

    @Test
    void accountLimitTakesEffectExactlyAtTheConfiguredValue() {
        for (int failure = 0; failure < ACCOUNT_LIMIT - 1; failure++) {
            assertThat(fail("victim", client())).isTrue();
        }
        assertThat(fail("victim", client())).as("failure number %d", ACCOUNT_LIMIT).isTrue();
        assertThat(fail("victim", client())).as("failure number %d", ACCOUNT_LIMIT + 1).isFalse();
    }

    @Test
    void bothLevelsReleaseOnceTheirWindowsExpire() {
        String client = client();
        exhaustClient("local", client);
        failuresBeforeAccountBlocks("global");
        assertThat(fail("local", client)).isFalse();
        assertThat(fail("global", client())).isFalse();

        clock.advance(WINDOW);

        assertThat(fail("local", client)).isTrue();
        assertThat(fail("global", client())).isTrue();
    }

    @Test
    void successDoesNotResetTheAccountCounterButClearsItsOwnClient() {
        String owner = client();
        for (int attempt = 0; attempt < CLIENT_LIMIT - 1; attempt++) {
            fail("victim", owner);
        }
        for (int client = 0; client < 19; client++) {
            exhaustClient("victim", client());
        }
        // 4 + 95 = 99 failures. A success must neither count nor reset them.
        try (LoginThrottle.Admission admission = throttle.admit("victim", owner)) {
            admission.succeeded();
        }

        assertThat(fail("victim", client())).as("failure number 100").isTrue();
        assertThat(fail("victim", client())).as("failure number 101").isFalse();
    }

    @Test
    void successClearsTheAccountAndClientCounter() {
        String owner = client();
        for (int attempt = 0; attempt < CLIENT_LIMIT - 1; attempt++) {
            fail("socio", owner);
        }
        try (LoginThrottle.Admission admission = throttle.admit("socio", owner)) {
            admission.succeeded();
        }

        for (int attempt = 0; attempt < CLIENT_LIMIT; attempt++) {
            assertThat(fail("socio", owner)).isTrue();
        }
        assertThat(fail("socio", owner)).isFalse();
    }

    @Test
    void accountsKeepIndependentCounters() {
        String client = client();
        exhaustClient("first", client);
        failuresBeforeAccountBlocks("second");

        assertThat(fail("third", client)).isTrue();
        assertThat(fail("first", client())).isTrue();
        assertThat(fail("second", client())).isFalse();
    }

    @Test
    void requestRefusedAtTheAccountLevelReturnsItsAccountAndClientSlot() {
        // Account-and-client window twice as long as the account window, so expiring the account
        // block does not also expire the client's slots: only the returned reservations explain
        // the five admissions at the end.
        throttle = new LoginThrottle(
                new InMemoryLoginRateLimiter(CLIENT_LIMIT, WINDOW.multipliedBy(2), 10_000, clock),
                new InMemoryLoginRateLimiter(ACCOUNT_LIMIT, WINDOW, 10_000, clock));
        failuresBeforeAccountBlocks("victim");
        String client = client();
        for (int refused = 0; refused < 20; refused++) {
            assertThat(fail("victim", client)).isFalse();
        }

        clock.advance(WINDOW);
        for (int attempt = 0; attempt < CLIENT_LIMIT; attempt++) {
            assertThat(fail("victim", client)).isTrue();
        }
        assertThat(fail("victim", client)).isFalse();
    }

    @Test
    void unrecordedOutcomeReturnsBothReservations() {
        String client = client();
        for (int attempt = 0; attempt < 50; attempt++) {
            throttle.admit("victim", client).close();
        }

        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT);
    }

    @Test
    void pairKeysCannotCollideAcrossAccountsAndClients() {
        exhaustClient("a", "1@b");
        assertThat(fail("a@1", "b")).isTrue();
        assertThat(fail("a", "1@b")).isFalse();
    }

    @Test
    void parallelRequestsFromOneClientCannotExceedTheAccountAndClientLimit() throws Exception {
        List<Boolean> outcomes = concurrently(64, index -> "198.51.100.1");

        assertThat(outcomes).filteredOn(admitted -> admitted).hasSize(CLIENT_LIMIT);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void parallelRequestsFromManyClientsCannotExceedTheAccountLimit() throws Exception {
        List<Boolean> outcomes = concurrently(250, index -> "2001:db8:" + index + "::/64");

        assertThat(outcomes).filteredOn(admitted -> admitted).hasSize(ACCOUNT_LIMIT);
    }

    private List<Boolean> concurrently(int requests, java.util.function.IntFunction<String> clientOf)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                String client = clientOf.apply(index);
                futures.add(executor.submit(() -> {
                    start.await();
                    return fail("victim", client);
                }));
            }
            start.countDown();
            List<Boolean> outcomes = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    /** One authentication failure; false when the throttle refused the attempt. */
    private boolean fail(String account, String client) {
        try (LoginThrottle.Admission admission = throttle.admit(account, client)) {
            admission.failed();
            return true;
        } catch (LoginRateLimitedException limited) {
            return false;
        }
    }

    private void exhaustClient(String account, String client) {
        for (int attempt = 0; attempt < CLIENT_LIMIT; attempt++) {
            assertThat(fail(account, client)).isTrue();
        }
        assertThat(fail(account, client)).isFalse();
    }

    /** Fails from fresh clients until the account blocks; returns how many failures fitted. */
    private int failuresBeforeAccountBlocks(String account) {
        int failures = 0;
        while (fail(account, client())) {
            failures++;
            assertThat(failures).isLessThanOrEqualTo(ACCOUNT_LIMIT);
        }
        return failures;
    }

    private String client() {
        nextClient++;
        return "10." + (nextClient >> 16 & 255) + "." + (nextClient >> 8 & 255) + "." + (nextClient & 255);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

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
