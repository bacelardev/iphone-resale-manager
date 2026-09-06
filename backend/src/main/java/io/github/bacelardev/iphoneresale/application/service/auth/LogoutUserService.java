package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenHasher;
import io.github.bacelardev.iphoneresale.application.port.security.AuthSessionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class LogoutUserService {

    private final AccessTokenHasher tokenHasher;
    private final AuthSessionStore sessionStore;
    private final Clock clock;

    public LogoutUserService(AccessTokenHasher tokenHasher, AuthSessionStore sessionStore, Clock clock) {
        this.tokenHasher = tokenHasher;
        this.sessionStore = sessionStore;
        this.clock = clock;
    }

    @Transactional
    public void logout(String rawToken) {
        sessionStore.revokeByTokenHash(tokenHasher.hash(rawToken), clock.instant());
    }
}
