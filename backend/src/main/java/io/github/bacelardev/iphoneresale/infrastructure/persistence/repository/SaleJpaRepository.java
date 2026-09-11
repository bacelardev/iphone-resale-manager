package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.enums.SaleStatus;
import io.github.bacelardev.iphoneresale.domain.model.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SaleJpaRepository extends JpaRepository<Sale, UUID> {

    boolean existsByDeviceIdAndStatus(UUID deviceId, SaleStatus status);

    boolean existsBySoldAtLessThanEqual(Instant soldAt);

    @EntityGraph(attributePaths = {"device", "responsibleUser", "cancelledBy"})
    Optional<Sale> findByDeviceIdAndStatus(UUID deviceId, SaleStatus status);

    @EntityGraph(attributePaths = {"device", "responsibleUser", "cancelledBy"})
    Optional<Sale> findTopByDeviceIdOrderByCreatedAtDesc(UUID deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sale from Sale sale
             where sale.device.id = :deviceId and sale.status = :status
            """)
    Optional<Sale> findByDeviceAndStatusForUpdate(
            @Param("deviceId") UUID deviceId,
            @Param("status") SaleStatus status
    );
}
