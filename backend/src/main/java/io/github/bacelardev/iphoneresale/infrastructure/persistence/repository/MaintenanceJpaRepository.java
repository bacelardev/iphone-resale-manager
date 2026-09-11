package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.Maintenance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceJpaRepository
        extends JpaRepository<Maintenance, UUID>, JpaSpecificationExecutor<Maintenance> {

    interface TotalProjection {
        UUID getMaintenanceId();

        BigDecimal getTotal();
    }

    boolean existsByDeviceIdAndStatus(UUID deviceId, MaintenanceStatus status);

    @Query("""
            select coalesce(sum(item.cost), 0)
              from MaintenanceItem item
             where item.maintenance.device.id = :deviceId
               and item.maintenance.status = io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus.ACTIVE
            """)
    BigDecimal sumActiveCostByDeviceId(@Param("deviceId") UUID deviceId);

    @Query("""
            select max(maintenance.performedAt)
              from Maintenance maintenance
             where maintenance.device.id = :deviceId
               and maintenance.status = io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus.ACTIVE
            """)
    Instant latestActivePerformedAtByDeviceId(@Param("deviceId") UUID deviceId);

    @Query("""
            select coalesce(sum(item.cost), 0)
              from MaintenanceItem item
             where item.maintenance.status = io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus.ACTIVE
               and item.maintenance.registrationOrigin = io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin.INITIAL_IMPORT
            """)
    BigDecimal sumActiveInitialImportCost();

    @Query("""
            select item.maintenance.id as maintenanceId, sum(item.cost) as total
              from MaintenanceItem item
             where item.maintenance.id in :ids
             group by item.maintenance.id
            """)
    List<TotalProjection> findTotals(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = {"device", "responsibleUser"})
    Page<Maintenance> findAll(Specification<Maintenance> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"device", "responsibleUser", "cancelledBy", "items", "items.part"})
    @Query("select m from Maintenance m where m.id = :id and m.device.id = :deviceId")
    Optional<Maintenance> findDetail(@Param("deviceId") UUID deviceId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Maintenance m where m.id = :id and m.device.id = :deviceId")
    Optional<Maintenance> findByIdForUpdate(
            @Param("deviceId") UUID deviceId,
            @Param("id") UUID id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from Maintenance m
             where m.device.id = :deviceId and m.status = :status
             order by m.id
            """)
    List<Maintenance> findByDeviceAndStatusForUpdate(
            @Param("deviceId") UUID deviceId,
            @Param("status") MaintenanceStatus status
    );

    boolean existsByRegistrationOriginAndPerformedAtLessThanEqual(
            RegistrationOrigin origin,
            Instant performedAt
    );
}
