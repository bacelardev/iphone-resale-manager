package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthSessionData;
import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenHasher;
import io.github.bacelardev.iphoneresale.application.port.security.AuthSessionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
public class AuthenticateAccessTokenService {

    private final AccessTokenHasher tokenHasher;
    private final AuthSessionStore sessionStore;
    private final Clock clock;

    public AuthenticateAccessTokenService(
            AccessTokenHasher tokenHasher,
            AuthSessionStore sessionStore,
            Clock clock
    ) {
        this.tokenHasher = tokenHasher;
        this.sessionStore = sessionStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AuthSessionData> authenticate(String rawToken) {
        return sessionStore.findActiveByTokenHash(tokenHasher.hash(rawToken), clock.instant());
    }
}
