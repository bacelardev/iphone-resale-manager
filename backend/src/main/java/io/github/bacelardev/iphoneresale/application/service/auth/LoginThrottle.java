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
 *   every client, and a successful login never resets it.</li>
 * </ol>
 *
 * <p>Both levels reserve a slot before the password is checked, so parallel requests cannot exceed
 * either limit. The reservations are kept when authentication fails and returned otherwise: a
 * request refused at the account level returns its account-and-client slot, a successful login
 * returns its account slot and clears its account-and-client counter, and an unexpected error
 * returns both. Unknown usernames are throttled exactly like existing ones.
 *
 * <p>One client contributes at most N failures per account-and-client window. That window is never
 * shorter than the account window, so at most two of them overlap any account window and one
 * client adds at most 2N failures to it; {@code AuthProperties} requires M &gt; 2N.
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
            accountClientLimiter.release(pairKey);
            throw new LoginRateLimitedException(global.retryAfterSeconds());
        }
        return new Admission(pairKey, normalizedUsername);
    }

    // Length-prefixed so no username or address can produce another pair's key.
    private static String pairKey(String normalizedUsername, String clientAddress) {
        return normalizedUsername.length() + ":" + normalizedUsername + "@" + clientAddress;
    }

    public final class Admission implements AutoCloseable {

        private final String pairKey;
        private final String accountKey;
        private boolean recorded;

        private Admission(String pairKey, String accountKey) {
            this.pairKey = pairKey;
            this.accountKey = accountKey;
        }

        /** Authentication failed: both reservations count as failures. */
        public void failed() {
            recorded = true;
        }

        /** Authentication succeeded: not a failure at either level; the account counter is kept. */
        public void succeeded() {
            recorded = true;
            accountClientLimiter.reset(pairKey);
            accountLimiter.release(accountKey);
        }

        @Override
        public void close() {
            if (!recorded) {
                recorded = true;
                accountClientLimiter.release(pairKey);
                accountLimiter.release(accountKey);
            }
        }
    }
}
