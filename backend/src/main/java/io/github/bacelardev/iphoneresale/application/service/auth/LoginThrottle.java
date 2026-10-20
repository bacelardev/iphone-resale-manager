package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.port.security.LoginAttemptLimiter;
import io.github.bacelardev.iphoneresale.config.LoginRateLimitConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Two-level per-account login throttle, applied after the per-client limit of the login filter.
 *
 * <ol>
 *   <li><b>Account and client:</b> at most N failures per window for one normalized username from
 *   one resolved client address. Reaching it blocks only that combination, and the attempts it
 *   rejects never reach the account level.</li>
 *   <li><b>Account:</b> at most M failures per window from any client. It may block the account for
 *   every client.</li>
 * </ol>
 *
 * <p>Both levels reserve a slot before the password is checked, so parallel requests cannot exceed
 * either limit. A failure keeps both reservations. Anything else returns exactly this attempt's own
 * reservations and nothing more: a request refused at the account level returns its
 * account-and-client slot, and a success or an unexpected error returns both. No outcome ever
 * clears failures already counted, at either level, nor the reservations of concurrent requests.
 * Unknown usernames are throttled exactly like existing ones.
 *
 * <p>Failures stay counted until their window expires, so one client accumulates at most N failures
 * per account-and-client window, whether or not it also succeeds. That window is never shorter than
 * the account window, so at most two of them overlap any account window and one client adds at most
 * 2N failures to it; {@code AuthProperties} requires M &gt; 2N.
 */
@Component
public class LoginThrottle {

    private final LoginAttemptLimiter accountClientLimiter;
    private final LoginAttemptLimiter accountLimiter;

    public LoginThrottle(
            @Qualifier(LoginRateLimitConfiguration.ACCOUNT_CLIENT_LIMITER) LoginAttemptLimiter accountClientLimiter,
            @Qualifier(LoginRateLimitConfiguration.ACCOUNT_LIMITER) LoginAttemptLimiter accountLimiter
    ) {
        this.accountClientLimiter = accountClientLimiter;
        this.accountLimiter = accountLimiter;
    }

    /**
     * Reserves one attempt at both levels or throws {@link LoginRateLimitedException}. The returned
     * admission must be closed; if neither outcome was recorded, closing returns both reservations.
     */
    public Admission admit(String normalizedUsername, String clientAddress) {
        String pairKey = pairKey(normalizedUsername, clientAddress);
        LoginAttemptLimiter.Decision local = accountClientLimiter.tryAcquire(pairKey);
        if (!local.allowed()) {
            throw new LoginRateLimitedException(local.retryAfterSeconds());
        }
        LoginAttemptLimiter.Decision global = accountLimiter.tryAcquire(normalizedUsername);
        if (!global.allowed()) {
            accountClientLimiter.release(pairKey, local);
            throw new LoginRateLimitedException(global.retryAfterSeconds());
        }
        return new Admission(pairKey, local, normalizedUsername, global);
    }

    // Length-prefixed so no username or address can produce another pair's key.
    private static String pairKey(String normalizedUsername, String clientAddress) {
        return normalizedUsername.length() + ":" + normalizedUsername + "@" + clientAddress;
    }

    /**
     * One admitted login attempt, holding one reservation at each level. It starts {@code PENDING}
     * and settles exactly once, into one of three terminal states:
     *
     * <ul>
     *   <li>{@code FAILED}: both reservations stay counted;</li>
     *   <li>{@code SUCCEEDED}: both reservations are returned, earlier failures stay;</li>
     *   <li>{@code ABANDONED}: closed without an outcome, both reservations are returned.</li>
     * </ul>
     *
     * <p>Reservations are returned at most once, on the single transition out of {@code PENDING}.
     * Repeating the outcome already recorded, or closing a settled admission, is an idempotent
     * no-op. Recording a different outcome after settling throws {@link IllegalStateException},
     * because the counters cannot be changed anymore: a failure stays counted and a returned
     * reservation is never returned twice.
     *
     * <p>Transitions are atomic: concurrent calls on the same admission settle it exactly once, and
     * the reservations are returned while the transition still holds the admission's lock. The
     * limiters never call back into an admission, so this lock cannot deadlock with theirs.
     */
    public final class Admission implements AutoCloseable {

        private enum State { PENDING, FAILED, SUCCEEDED, ABANDONED }

        private final String pairKey;
        private final LoginAttemptLimiter.Decision localReservation;
        private final String accountKey;
        private final LoginAttemptLimiter.Decision accountReservation;
        private State state = State.PENDING;

        private Admission(String pairKey, LoginAttemptLimiter.Decision localReservation,
                          String accountKey, LoginAttemptLimiter.Decision accountReservation) {
            this.pairKey = pairKey;
            this.localReservation = localReservation;
            this.accountKey = accountKey;
            this.accountReservation = accountReservation;
        }

        /** Authentication failed: both reservations stay counted as failures. */
        public void failed() {
            settle(State.FAILED);
        }

        /** Authentication succeeded: returns this attempt's reservations; earlier failures stay. */
        public void succeeded() {
            settle(State.SUCCEEDED);
        }

        /** Returns the reservations of an admission that recorded no outcome; otherwise a no-op. */
        @Override
        public synchronized void close() {
            if (state == State.PENDING) {
                settle(State.ABANDONED);
            }
        }

        private synchronized void settle(State outcome) {
            if (state == outcome) {
                return;
            }
            if (state != State.PENDING) {
                throw new IllegalStateException("Login admission already settled as " + state);
            }
            state = outcome;
            if (outcome != State.FAILED) {
                accountClientLimiter.release(pairKey, localReservation);
                accountLimiter.release(accountKey, accountReservation);
            }
        }
    }
}
