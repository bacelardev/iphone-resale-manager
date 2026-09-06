package io.github.bacelardev.iphoneresale.infrastructure.security.persistence;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthUserData;
import io.github.bacelardev.iphoneresale.application.dto.auth.CreateBootstrapUser;
import io.github.bacelardev.iphoneresale.application.port.security.AuthUserStore;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AppUserJpaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAuthUserStore implements AuthUserStore {

    private static final long BOOTSTRAP_ADVISORY_LOCK = 4_287_840_939_950L;

    private final AppUserJpaRepository repository;
    private final EntityManager entityManager;

    public JpaAuthUserStore(AppUserJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<AuthUserData> findByUsername(String normalizedUsername) {
        return repository.findByUsername(normalizedUsername).map(JpaAuthUserStore::toData);
    }

    @Override
    public Optional<AuthUserData> findActiveById(UUID id) {
        return repository.findByIdAndActiveTrue(id).map(JpaAuthUserStore::toData);
    }

    @Override
    public long countUsers() {
        return repository.count();
    }

    @Override
    public void acquireBootstrapLock() {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:lockId)")
                .setParameter("lockId", BOOTSTRAP_ADVISORY_LOCK)
                .getSingleResult();
    }

    @Override
    public AuthUserData createBootstrapUser(CreateBootstrapUser command) {
        AppUser user = AppUser.bootstrapSocio(command.name(), command.username(), command.passwordHash());
        return toData(repository.saveAndFlush(user));
    }

    static AuthUserData toData(AppUser user) {
        return new AuthUserData(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion() == null ? 0 : user.getVersion()
        );
    }
}
