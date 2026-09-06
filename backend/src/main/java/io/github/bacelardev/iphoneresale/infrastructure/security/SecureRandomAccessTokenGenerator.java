package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureRandomAccessTokenGenerator implements AccessTokenGenerator {

    public static final String PREFIX = "irs_";
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
