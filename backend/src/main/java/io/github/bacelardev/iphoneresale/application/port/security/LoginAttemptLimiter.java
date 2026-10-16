package io.github.bacelardev.iphoneresale.application.port.security;

/**
 * Bounded, expiring login attempt counter. Every attempt is reserved before the credentials are
 * checked, so concurrent requests cannot exceed the limit; a successful login resets its key.
 */
public interface LoginAttemptLimiter {

    Decision tryAcquire(String key);

    void reset(String key);

    record Decision(boolean allowed, long retryAfterSeconds) {

        public static Decision permit() {
            return new Decision(true, 0);
        }

        public static Decision rejected(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
