package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.domain.model.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface MaintenanceJpaRepository extends JpaRepository<Maintenance, UUID> {

    boolean existsByDeviceIdAndStatus(UUID deviceId, MaintenanceStatus status);

    @Query("""
            select coalesce(sum(item.cost), 0)
              from MaintenanceItem item
             where item.maintenance.device.id = :deviceId
               and item.maintenance.status = io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus.ACTIVE
            """)
    BigDecimal sumActiveCostByDeviceId(@Param("deviceId") UUID deviceId);
}
