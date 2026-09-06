package io.github.bacelardev.iphoneresale.infrastructure.security.persistence;

import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.domain.model.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_session")
public class AuthSessionEntity extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSessionEntity() {
    }

    public static AuthSessionEntity issue(
            AppUser user,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        AuthSessionEntity session = new AuthSessionEntity();
        session.user = user;
        session.tokenHash = tokenHash;
        session.createdAt = createdAt;
        session.expiresAt = expiresAt;
        return session;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
