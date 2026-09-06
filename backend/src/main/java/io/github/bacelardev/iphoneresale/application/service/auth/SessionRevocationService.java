package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.port.security.AuthSessionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class SessionRevocationService {

    private final AuthSessionStore sessionStore;
    private final Clock clock;

    public SessionRevocationService(AuthSessionStore sessionStore, Clock clock) {
        this.sessionStore = sessionStore;
        this.clock = clock;
    }

    @Transactional
    public int revokeAllSessionsForUser(UUID userId) {
        return sessionStore.revokeAllForUser(userId, clock.instant());
    }
}
