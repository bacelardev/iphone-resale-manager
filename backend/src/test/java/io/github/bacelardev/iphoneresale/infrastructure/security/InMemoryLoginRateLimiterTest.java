package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.application.port.security.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLoginRateLimiterTest {

    @Test
    void rejectsAfterLimitAndExpiresTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-06T12:00:00Z"));
        InMemoryLoginRateLimiter limiter =
                new InMemoryLoginRateLimiter(2, Duration.ofMinutes(1), 100, clock);

        assertThat(limiter.tryAcquire("192.0.2.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("192.0.2.1").allowed()).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("192.0.2.1").allowed()).isTrue();
    }

    @Test
    void resetsOnSuccessSignalAndRemainsBounded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-06T12:00:00Z"));
        InMemoryLoginRateLimiter limiter =
                new InMemoryLoginRateLimiter(2, Duration.ofMinutes(1), 2, clock);

        limiter.tryAcquire("one");
        limiter.reset("one");
        assertThat(limiter.tryAcquire("one").allowed()).isTrue();

        limiter.tryAcquire("two");
        limiter.tryAcquire("three");
        assertThat(limiter.trackedKeyCount()).isEqualTo(2);
    }

    @Test
    void releaseReturnsOneAttemptWithoutLiftingOtherAttempts() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
                3, Duration.ofMinutes(1), 100, new MutableClock(Instant.parse("2026-09-06T12:00:00Z")));

        limiter.tryAcquire("key");
        LoginAttemptLimiter.Decision second = limiter.tryAcquire("key");
        limiter.tryAcquire("key");
        assertThat(limiter.tryAcquire("key").allowed()).isFalse();

        limiter.release("key", second);
        assertThat(limiter.tryAcquire("key").allowed()).isTrue();
        assertThat(limiter.tryAcquire("key").allowed()).isFalse();

        limiter.release("unknown", second);
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void releaseOfAReservationFromAnExpiredWindowLeavesTheNewWindowUntouched() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-06T12:00:00Z"));
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(2, Duration.ofMinutes(1), 100, clock);
        LoginAttemptLimiter.Decision stale = limiter.tryAcquire("key");

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("key").allowed()).isTrue();
        limiter.release("key", stale);

        assertThat(limiter.tryAcquire("key").allowed()).isTrue();
        assertThat(limiter.tryAcquire("key").allowed()).isFalse();
    }

    @Test
    void releaseOfAReservationTakenBeforeAResetLeavesTheNewWindowUntouched() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
                2, Duration.ofMinutes(1), 100, new MutableClock(Instant.parse("2026-09-06T12:00:00Z")));
        LoginAttemptLimiter.Decision beforeReset = limiter.tryAcquire("key");

        limiter.reset("key");
        assertThat(limiter.tryAcquire("key").allowed()).isTrue();
        limiter.release("key", beforeReset);

        assertThat(limiter.tryAcquire("key").allowed()).isTrue();
        assertThat(limiter.tryAcquire("key").allowed()).isFalse();
    }

    @Test
    void releasingARejectionReturnsNothing() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
                1, Duration.ofMinutes(1), 100, new MutableClock(Instant.parse("2026-09-06T12:00:00Z")));
        limiter.tryAcquire("key");
        LoginAttemptLimiter.Decision rejection = limiter.tryAcquire("key");

        limiter.release("key", rejection);

        assertThat(rejection.allowed()).isFalse();
        assertThat(limiter.tryAcquire("key").allowed()).isFalse();
    }

    @Test
    void rejectedAttemptsNeverAllocateMemory() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
                1, Duration.ofMinutes(15), 100, new MutableClock(Instant.parse("2026-09-06T12:00:00Z")));
        limiter.tryAcquire("blocked");

        for (int attempt = 0; attempt < 1_000; attempt++) {
            assertThat(limiter.tryAcquire("blocked").allowed()).isFalse();
        }
        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void fullMapEvictsNonBlockingKeysBeforeAnActiveBlock() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
                2, Duration.ofMinutes(15), 3, new MutableClock(Instant.parse("2026-09-06T12:00:00Z")));
        limiter.tryAcquire("victim");
        limiter.tryAcquire("victim");
        assertThat(limiter.tryAcquire("victim").allowed()).isFalse();

        for (int flood = 0; flood < 100; flood++) {
            limiter.tryAcquire("flood-" + flood);
        }

        assertThat(limiter.trackedKeyCount()).isEqualTo(3);
        assertThat(limiter.tryAcquire("victim").allowed()).isFalse();
    }

    @Test
    void mapFullOfActiveBlocksStaysBounded() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(
                1, Duration.ofMinutes(15), 3, new MutableClock(Instant.parse("2026-09-06T12:00:00Z")));

        for (int key = 0; key < 10; key++) {
            limiter.tryAcquire("blocked-" + key);
        }

        assertThat(limiter.trackedKeyCount()).isEqualTo(3);
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
            return ZoneId.of("UTC");
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
