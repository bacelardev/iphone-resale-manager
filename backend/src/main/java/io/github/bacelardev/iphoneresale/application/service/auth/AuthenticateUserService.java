package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthUserData;
import io.github.bacelardev.iphoneresale.application.dto.auth.IssuedAccessToken;
import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenGenerator;
import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenHasher;
import io.github.bacelardev.iphoneresale.application.port.security.AuthSessionStore;
import io.github.bacelardev.iphoneresale.application.port.security.AuthUserStore;
import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import io.github.bacelardev.iphoneresale.config.properties.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthenticateUserService {

    private final AuthUserStore userStore;
    private final AuthSessionStore sessionStore;
    private final PasswordHashService passwordHashService;
    private final AccessTokenGenerator tokenGenerator;
    private final AccessTokenHasher tokenHasher;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthenticateUserService(
            AuthUserStore userStore,
            AuthSessionStore sessionStore,
            PasswordHashService passwordHashService,
            AccessTokenGenerator tokenGenerator,
            AccessTokenHasher tokenHasher,
            AuthProperties properties,
            Clock clock
    ) {
        this.userStore = userStore;
        this.sessionStore = sessionStore;
        this.passwordHashService = passwordHashService;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordHashService.encode("dummy-authentication-value");
    }

    @Transactional
    public IssuedAccessToken authenticate(String username, String password) {
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        Optional<AuthUserData> candidate = userStore.findByUsername(normalizedUsername);
        String storedHash = candidate.map(AuthUserData::passwordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordHashService.matches(password, storedHash);

        if (candidate.isEmpty() || !candidate.orElseThrow().active() || !passwordMatches) {
            throw new AuthenticationFailedException();
        }

        AuthUserData user = candidate.orElseThrow();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.tokenTtl());
        String rawToken = tokenGenerator.generate();
        sessionStore.create(user.id(), tokenHasher.hash(rawToken), issuedAt, expiresAt);
        return new IssuedAccessToken(rawToken, expiresAt, user);
    }
}
