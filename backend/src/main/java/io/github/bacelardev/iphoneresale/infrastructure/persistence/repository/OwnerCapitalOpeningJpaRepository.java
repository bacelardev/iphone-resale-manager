package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.OwnerCapitalOpening;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OwnerCapitalOpeningJpaRepository extends JpaRepository<OwnerCapitalOpening, UUID> {

    @EntityGraph(attributePaths = {"ownerUser"})
    List<OwnerCapitalOpening> findByBusinessInitializationIdOrderByOwnerUserNameAsc(UUID initializationId);
}
