package io.github.bacelardev.iphoneresale.application.port.security;

/**
 * Bounded, expiring login attempt counter. An attempt is reserved before the credentials are
 * checked, so concurrent requests cannot exceed the limit. The caller then keeps the reservation
 * (a failure), {@link #release(String) releases} it (not a failure) or {@link #reset(String) resets}
 * the key.
 */
public interface LoginAttemptLimiter {

    Decision tryAcquire(String key);

    /** Returns one previously acquired attempt; never lifts a block caused by other attempts. */
    void release(String key);

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
