package io.github.bacelardev.iphoneresale.infrastructure.security;

import java.util.regex.Pattern;

public final class BearerTokenFormat {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^irs_[A-Za-z0-9_-]{43}$");

    private BearerTokenFormat() {
    }

    public static boolean isValid(String token) {
        return token != null && TOKEN_PATTERN.matcher(token).matches();
    }
}
