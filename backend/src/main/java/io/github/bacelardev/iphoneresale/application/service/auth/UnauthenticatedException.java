package io.github.bacelardev.iphoneresale.application.service.auth;

public final class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Authentication is required");
    }
}
