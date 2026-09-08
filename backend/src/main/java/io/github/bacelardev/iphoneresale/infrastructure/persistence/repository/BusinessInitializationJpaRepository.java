package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface BusinessInitializationJpaRepository
        extends JpaRepository<BusinessInitialization, UUID> {

    @Query("select b from BusinessInitialization b")
    Optional<BusinessInitialization> findSingleton();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BusinessInitialization b")
    Optional<BusinessInitialization> findSingletonForUpdate();
}
