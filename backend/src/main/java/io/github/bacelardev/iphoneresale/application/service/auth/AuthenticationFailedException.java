package io.github.bacelardev.iphoneresale.application.service.auth;

public final class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Authentication failed");
    }
}
