package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserJpaRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByIdAndActiveTrue(UUID id);
}
