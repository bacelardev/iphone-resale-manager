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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    void successDoesNotResetTheAccountCounter() {
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
    void successKeepsEarlierFailuresOfTheSameAccountAndClient() {
        String owner = client();
        for (int attempt = 0; attempt < CLIENT_LIMIT - 1; attempt++) {
            assertThat(fail("socio", owner)).isTrue();
        }
        assertThat(succeed("socio", owner)).isTrue();

        assertThat(fail("socio", owner)).as("failure number %d", CLIENT_LIMIT).isTrue();
        assertThat(succeed("socio", owner)).as("correct password after %d failures", CLIENT_LIMIT).isFalse();
        assertThat(fail("socio", owner)).isFalse();
    }

    @Test
    void successReturnsExactlyItsOwnReservation() {
        String owner = client();
        assertThat(fail("socio", owner)).isTrue();
        assertThat(fail("socio", owner)).isTrue();
        assertThat(succeed("socio", owner)).isTrue();

        // Two failures kept and the success slot returned: exactly three more failures fit.
        for (int attempt = 0; attempt < CLIENT_LIMIT - 2; attempt++) {
            assertThat(fail("socio", owner)).isTrue();
        }
        assertThat(fail("socio", owner)).isFalse();
    }

    @Test
    void successDoesNotEraseReservationsOfConcurrentRequestsFromTheSameClient() {
        String client = client();
        LoginThrottle.Admission succeeding = throttle.admit("victim", client);
        LoginThrottle.Admission failingFirst = throttle.admit("victim", client);
        LoginThrottle.Admission failingSecond = throttle.admit("victim", client);

        succeeding.succeeded();
        succeeding.close();
        failingFirst.failed();
        failingFirst.close();
        failingSecond.failed();
        failingSecond.close();

        for (int attempt = 0; attempt < CLIENT_LIMIT - 2; attempt++) {
            assertThat(fail("victim", client)).isTrue();
        }
        assertThat(fail("victim", client)).isFalse();
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void oneClientKnowingThePasswordCannotReachTheAccountLimitByInterleavingSuccesses() {
        String client = client();
        Map<Outcome, Integer> tally = new EnumMap<>(Outcome.class);
        List<Outcome> sequence = new ArrayList<>();
        for (int cycle = 0; cycle < 20; cycle++) {
            sequence.add(record(tally, attempt("victim", client, false)));
            sequence.add(record(tally, attempt("victim", client, true)));
        }

        // Failures are never forgiven: each success is admitted only while the client still has
        // room, so the client alternates until its fifth failure and is refused from then on.
        assertThat(sequence.subList(0, 10)).containsExactly(
                Outcome.FAILED, Outcome.SUCCEEDED, Outcome.FAILED, Outcome.SUCCEEDED,
                Outcome.FAILED, Outcome.SUCCEEDED, Outcome.FAILED, Outcome.SUCCEEDED,
                Outcome.FAILED, Outcome.REFUSED);
        assertThat(tally).containsEntry(Outcome.FAILED, CLIENT_LIMIT)
                .containsEntry(Outcome.SUCCEEDED, CLIENT_LIMIT - 1)
                .containsEntry(Outcome.REFUSED, 40 - (2 * CLIENT_LIMIT - 1));

        assertThat(attempt("victim", client(), true)).as("another legitimate client").isEqualTo(Outcome.SUCCEEDED);
        assertThat(failuresBeforeAccountBlocks("victim")).as("account counter holds exactly the 5 failures")
                .isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void oneClientInterleavingFailuresAndSuccessesCannotBlockOthersOverManyWindows() {
        String attacker = client();
        String owner = client();
        Map<Outcome, Integer> attackerTally = new EnumMap<>(Outcome.class);
        Map<Outcome, Integer> ownerTally = new EnumMap<>(Outcome.class);
        int minutes = 24 * 60;
        for (int minute = 0; minute < minutes; minute++) {
            for (int attempt = 0; attempt < CLIENT_LIMIT - 1; attempt++) {
                record(attackerTally, attempt("victim", attacker, false));
            }
            record(attackerTally, attempt("victim", attacker, true));
            if (minute % 10 == 5) {
                assertThat(record(ownerTally, attempt("victim", owner, true)))
                        .as("owner at minute %d", minute).isEqualTo(Outcome.SUCCEEDED);
            }
            clock.advance(Duration.ofMinutes(1));
        }

        // Every 15-minute window: minute 0 admits 4 failures and the success; minute 1 admits the
        // fifth failure and refuses the other 4 requests; minutes 2-14 refuse all 5 requests.
        int windows = minutes / (int) WINDOW.toMinutes();
        assertThat(attackerTally).containsEntry(Outcome.FAILED, windows * CLIENT_LIMIT)
                .containsEntry(Outcome.SUCCEEDED, windows)
                .containsEntry(Outcome.REFUSED, windows * (4 + 13 * 5));
        assertThat(ownerTally).containsEntry(Outcome.SUCCEEDED, minutes / 10)
                .doesNotContainKey(Outcome.REFUSED);
    }

    @Test
    void reservationFromAnExpiredWindowCannotLowerTheNewWindow() {
        String client = client();
        LoginThrottle.Admission stale = throttle.admit("victim", client);

        clock.advance(WINDOW);
        assertThat(fail("victim", client)).isTrue();
        stale.succeeded();
        stale.close();

        for (int attempt = 0; attempt < CLIENT_LIMIT - 1; attempt++) {
            assertThat(fail("victim", client)).isTrue();
        }
        assertThat(fail("victim", client)).isFalse();
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void parallelSuccessesAndFailuresFromOneClientNeverExceedTheLimitNorEraseFailures() throws Exception {
        for (int round = 0; round < 25; round++) {
            String account = "mixed." + round;
            String client = client();
            int recordedFailures = concurrentlyMixed(account, client, 64, round % 2 == 0);

            assertThat(recordedFailures).as("round %d", round).isBetween(0, CLIENT_LIMIT);
            assertThat(remainingClientFailures(account, client)).as("round %d", round)
                    .isEqualTo(CLIENT_LIMIT - recordedFailures);
            assertThat(failuresBeforeAccountBlocks(account)).as("round %d", round)
                    .isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
        }
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
    void repeatedSucceededReturnsTheReservationsOnlyOnce() {
        String client = client();
        assertThat(fail("victim", client)).isTrue();
        assertThat(fail("victim", client)).isTrue();
        LoginThrottle.Admission admission = throttle.admit("victim", client);
        admission.succeeded();
        admission.succeeded();
        admission.succeeded();
        admission.close();

        // Two failures kept, one success slot returned once: exactly three more failures fit.
        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT - 2);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void repeatedCloseReturnsTheReservationsOnlyOnce() {
        String client = client();
        assertThat(fail("victim", client)).isTrue();
        LoginThrottle.Admission admission = throttle.admit("victim", client);
        admission.close();
        admission.close();
        admission.close();

        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT - 1);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void succeededFollowedByCloseReleasesExactlyOnce() {
        String client = client();
        assertThat(fail("victim", client)).isTrue();
        assertThat(fail("victim", client)).isTrue();
        assertThat(fail("victim", client)).isTrue();
        try (LoginThrottle.Admission admission = throttle.admit("victim", client)) {
            admission.succeeded();
        }

        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT - 3);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void failedFollowedBySucceededKeepsTheFailureAndRejectsTheContradiction() {
        String client = client();
        LoginThrottle.Admission admission = throttle.admit("victim", client);
        admission.failed();

        assertThatThrownBy(admission::succeeded).isInstanceOf(IllegalStateException.class);
        admission.failed();
        admission.close();

        // The recorded failure plus four more fill the client; all five stay on the account too.
        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT - 1);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void succeededFollowedByFailedCannotCountAReturnedReservation() {
        String client = client();
        LoginThrottle.Admission admission = throttle.admit("victim", client);
        admission.succeeded();

        assertThatThrownBy(admission::failed).isInstanceOf(IllegalStateException.class);
        admission.close();

        // Nothing was counted: the client still fits five failures, which then count on the account.
        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void outcomeAfterCloseIsRejectedWithoutTouchingTheCounters() {
        String client = client();
        LoginThrottle.Admission abandoned = throttle.admit("victim", client);
        abandoned.close();

        assertThatThrownBy(abandoned::failed).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(abandoned::succeeded).isInstanceOf(IllegalStateException.class);

        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void repeatedSettlingOfConcurrentAdmissionsReturnsEachReservationOnce() {
        String client = client();
        LoginThrottle.Admission first = throttle.admit("victim", client);
        LoginThrottle.Admission second = throttle.admit("victim", client);
        LoginThrottle.Admission third = throttle.admit("victim", client);

        first.succeeded();
        first.succeeded();
        first.close();
        second.failed();
        second.failed();
        second.close();
        third.close();
        third.close();

        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT - 1);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    @Test
    void repeatedSucceededOnAStaleAdmissionNeverLowersTheNewWindow() {
        String client = client();
        LoginThrottle.Admission stale = throttle.admit("victim", client);

        clock.advance(WINDOW);
        assertThat(fail("victim", client)).isTrue();
        stale.succeeded();
        stale.succeeded();
        stale.close();

        assertThat(remainingClientFailures("victim", client)).isEqualTo(CLIENT_LIMIT - 1);
        assertThat(failuresBeforeAccountBlocks("victim")).isEqualTo(ACCOUNT_LIMIT - CLIENT_LIMIT);
    }

    /**
     * Races eight threads on the same admission, half the time with contradictory outcomes. The race
     * window is a few nanoseconds, so the test repeats it many times: without an atomic transition
     * it was observed to fail after several thousand rounds, so a pass is strong but probabilistic
     * evidence. The guarantee itself comes from the synchronized transition.
     */
    @Test
    void concurrentSettlingOfOneAdmissionReturnsEachReservationAtMostOnce() throws Exception {
        int accountLimit = 2 * CLIENT_LIMIT + 1;
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 50_000; round++) {
                throttle = new LoginThrottle(
                        new InMemoryLoginRateLimiter(CLIENT_LIMIT, WINDOW, 1_000, clock),
                        new InMemoryLoginRateLimiter(accountLimit, WINDOW, 1_000, clock));
                String client = client();
                assertThat(fail("victim", client)).isTrue();
                assertThat(fail("victim", client)).isTrue();
                LoginThrottle.Admission admission = throttle.admit("victim", client);
                boolean contradictory = round % 2 == 1;

                CountDownLatch start = new CountDownLatch(1);
                List<Future<Outcome>> rejectedOutcomes = new ArrayList<>();
                for (int index = 0; index < threads; index++) {
                    Outcome outcome = contradictory && index % 2 == 1 ? Outcome.FAILED : Outcome.SUCCEEDED;
                    rejectedOutcomes.add(executor.submit(() -> {
                        start.await();
                        try {
                            if (outcome == Outcome.FAILED) admission.failed();
                            else admission.succeeded();
                            return null;
                        } catch (IllegalStateException contradiction) {
                            return outcome;
                        } finally {
                            admission.close();
                        }
                    }));
                }
                start.countDown();
                List<Outcome> rejected = new ArrayList<>();
                for (Future<Outcome> future : rejectedOutcomes) {
                    Outcome loser = future.get(10, TimeUnit.SECONDS);
                    if (loser != null) rejected.add(loser);
                }

                // Exactly one outcome wins: every call of the other kind is rejected, none of its own.
                Outcome winner = rejected.contains(Outcome.FAILED) ? Outcome.SUCCEEDED
                        : contradictory ? Outcome.FAILED : Outcome.SUCCEEDED;
                assertThat(rejected).as("round %d", round).doesNotContain(winner);
                if (contradictory) assertThat(rejected).as("round %d", round).hasSize(threads / 2);
                else assertThat(rejected).as("round %d", round).isEmpty();

                int clientFailures = 2 + (winner == Outcome.FAILED ? 1 : 0);
                assertThat(remainingClientFailures("victim", client)).as("round %d, %s", round, winner)
                        .isEqualTo(CLIENT_LIMIT - clientFailures);
                assertThat(failuresBeforeAccountBlocks("victim")).as("round %d, %s", round, winner)
                        .isEqualTo(accountLimit - CLIENT_LIMIT);
            }
        } finally {
            executor.shutdownNow();
        }
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

    private enum Outcome { FAILED, SUCCEEDED, REFUSED }

    /** One authentication with the given password correctness, reporting what actually happened. */
    private Outcome attempt(String account, String client, boolean correctPassword) {
        try (LoginThrottle.Admission admission = throttle.admit(account, client)) {
            if (correctPassword) {
                admission.succeeded();
                return Outcome.SUCCEEDED;
            }
            admission.failed();
            return Outcome.FAILED;
        } catch (LoginRateLimitedException limited) {
            return Outcome.REFUSED;
        }
    }

    private static Outcome record(Map<Outcome, Integer> tally, Outcome outcome) {
        tally.merge(outcome, 1, Integer::sum);
        return outcome;
    }

    /** One successful authentication; false when the throttle refused the attempt. */
    private boolean succeed(String account, String client) {
        try (LoginThrottle.Admission admission = throttle.admit(account, client)) {
            admission.succeeded();
            return true;
        } catch (LoginRateLimitedException limited) {
            return false;
        }
    }

    /**
     * Fires requests from one client in parallel, alternating correct and wrong passwords. With
     * {@code holdReservations}, every admission stays open until all requests were admitted or
     * refused, so successes complete while the other reservations are still in flight; without it,
     * successes return their slots while later requests are still arriving. Returns how many
     * failures were recorded.
     */
    private int concurrentlyMixed(String account, String client, int requests, boolean holdReservations)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch decided = new CountDownLatch(requests);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                boolean correctPassword = index % 2 == 0;
                futures.add(executor.submit(() -> {
                    start.await();
                    LoginThrottle.Admission admission;
                    try {
                        admission = throttle.admit(account, client);
                    } catch (LoginRateLimitedException limited) {
                        decided.countDown();
                        return false;
                    }
                    decided.countDown();
                    try (admission) {
                        if (holdReservations) decided.await(10, TimeUnit.SECONDS);
                        if (correctPassword) {
                            admission.succeeded();
                            return false;
                        }
                        admission.failed();
                        return true;
                    }
                }));
            }
            start.countDown();
            int failures = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) failures++;
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    /** Fails from {@code client} until it is blocked; returns how many failures still fitted. */
    private int remainingClientFailures(String account, String client) {
        int failures = 0;
        while (fail(account, client)) {
            failures++;
            assertThat(failures).isLessThanOrEqualTo(CLIENT_LIMIT);
        }
        return failures;
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
