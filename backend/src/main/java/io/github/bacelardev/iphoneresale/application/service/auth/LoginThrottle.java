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

    public final class Admission implements AutoCloseable {

        private final String pairKey;
        private final LoginAttemptLimiter.Decision localReservation;
        private final String accountKey;
        private final LoginAttemptLimiter.Decision accountReservation;
        private boolean recorded;

        private Admission(String pairKey, LoginAttemptLimiter.Decision localReservation,
                          String accountKey, LoginAttemptLimiter.Decision accountReservation) {
            this.pairKey = pairKey;
            this.localReservation = localReservation;
            this.accountKey = accountKey;
            this.accountReservation = accountReservation;
        }

        /** Authentication failed: both reservations stay counted as failures. */
        public void failed() {
            recorded = true;
        }

        /** Authentication succeeded: returns this attempt's reservations; earlier failures stay. */
        public void succeeded() {
            recorded = true;
            returnReservations();
        }

        @Override
        public void close() {
            if (!recorded) {
                recorded = true;
                returnReservations();
            }
        }

        private void returnReservations() {
            accountClientLimiter.release(pairKey, localReservation);
            accountLimiter.release(accountKey, accountReservation);
        }
    }
}
