package io.github.bacelardev.iphoneresale.infrastructure.security.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, UUID> {

    @Query("""
            select session
              from AuthSessionEntity session
              join fetch session.user user
             where session.tokenHash = :tokenHash
               and session.revokedAt is null
               and session.expiresAt > :now
               and user.active = true
            """)
    Optional<AuthSessionEntity> findActive(
            @Param("tokenHash") String tokenHash,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :revokedAt
             where session.tokenHash = :tokenHash
               and session.revokedAt is null
            """)
    int revokeByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("revokedAt") Instant revokedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :revokedAt
             where session.user.id = :userId
               and session.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt
    );
}
