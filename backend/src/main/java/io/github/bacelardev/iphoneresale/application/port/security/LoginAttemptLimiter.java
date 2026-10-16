package io.github.bacelardev.iphoneresale.application.port.security;

/**
 * Bounded, expiring login attempt counter. An attempt is reserved before the credentials are
 * checked, so concurrent requests cannot exceed the limit. The caller then keeps the reservation
 * (a failure) or {@link #release(String, Decision) releases} it (not a failure).
 */
public interface LoginAttemptLimiter {

    Decision tryAcquire(String key);

    /**
     * Returns exactly the attempt reserved by {@code acquired}. Every other attempt counted for the
     * key is kept, and the call is ignored if that reservation's window has since expired or been
     * reset, so it can never lower the count of a newer window.
     */
    void release(String key, Decision acquired);

    void reset(String key);

    /**
     * @param window identifies the counting window that holds the reservation of a permitted
     *               attempt; {@link #NO_WINDOW} for a rejection, which reserves nothing
     */
    record Decision(boolean allowed, long retryAfterSeconds, long window) {

        public static final long NO_WINDOW = 0;

        public static Decision permit(long window) {
            return new Decision(true, 0, window);
        }

        public static Decision rejected(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds, NO_WINDOW);
        }
    }
}
