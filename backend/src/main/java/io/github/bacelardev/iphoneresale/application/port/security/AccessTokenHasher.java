package io.github.bacelardev.iphoneresale.application.port.security;

public interface AccessTokenHasher {

    String hash(String rawToken);
}
