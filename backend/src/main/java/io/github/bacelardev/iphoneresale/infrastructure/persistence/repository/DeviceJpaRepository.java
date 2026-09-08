package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.Device;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface DeviceJpaRepository
        extends JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :id")
    Optional<Device> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByRegistrationOrigin(RegistrationOrigin registrationOrigin);

    long countByRegistrationOriginAndArchivedAtIsNull(RegistrationOrigin registrationOrigin);

    @Query("""
            select coalesce(sum(d.purchasePrice), 0)
              from Device d
             where d.registrationOrigin = :origin
               and d.archivedAt is null
            """)
    BigDecimal sumPurchasePriceByOrigin(@Param("origin") RegistrationOrigin registrationOrigin);
}
