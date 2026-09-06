package io.github.bacelardev.iphoneresale.infrastructure.security.persistence;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthSessionData;
import io.github.bacelardev.iphoneresale.application.port.security.AuthSessionStore;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAuthSessionStore implements AuthSessionStore {

    private final AuthSessionJpaRepository repository;
    private final EntityManager entityManager;

    public JpaAuthSessionStore(AuthSessionJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void create(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        AppUser user = entityManager.getReference(AppUser.class, userId);
        repository.save(AuthSessionEntity.issue(user, tokenHash, createdAt, expiresAt));
    }

    @Override
    public Optional<AuthSessionData> findActiveByTokenHash(String tokenHash, Instant now) {
        return repository.findActive(tokenHash, now)
                .map(session -> new AuthSessionData(
                        session.getId(),
                        JpaAuthUserStore.toData(session.getUser()),
                        session.getExpiresAt()
                ));
    }

    @Override
    public int revokeByTokenHash(String tokenHash, Instant revokedAt) {
        return repository.revokeByTokenHash(tokenHash, revokedAt);
    }

    @Override
    public int revokeAllForUser(UUID userId, Instant revokedAt) {
        return repository.revokeAllForUser(userId, revokedAt);
    }
}
