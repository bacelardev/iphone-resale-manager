package io.github.bacelardev.iphoneresale.application.port.security;

public interface PasswordHashService {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
