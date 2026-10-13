package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AppUserJpaRepository extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByIdAndActiveTrue(UUID id);

    boolean existsByUsernameAndIdNot(String username, UUID id);

    long countByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.active = true order by user.id")
    List<AppUser> findAllActiveForUpdate();
}
