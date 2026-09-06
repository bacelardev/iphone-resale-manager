package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHashService implements PasswordHashService {

    private static final int STRENGTH = 12;

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(STRENGTH);

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}
