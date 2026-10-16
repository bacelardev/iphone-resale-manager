package io.github.bacelardev.iphoneresale.application.service.auth;

/**
 * Login refused before the credentials were checked. The public response is identical whichever
 * limit was reached and whether or not the account exists.
 */
public final class LoginRateLimitedException extends RuntimeException {

    public static final String CODE = "LOGIN_RATE_LIMITED";
    public static final String MESSAGE = "Muitas tentativas de autenticação. Tente novamente mais tarde.";

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Login rate limited");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
