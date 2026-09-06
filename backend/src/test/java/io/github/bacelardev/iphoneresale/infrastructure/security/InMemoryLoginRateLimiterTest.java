package io.github.bacelardev.iphoneresale.infrastructure.security;

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
        assertThat(limiter.trackedClientCount()).isEqualTo(2);
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
