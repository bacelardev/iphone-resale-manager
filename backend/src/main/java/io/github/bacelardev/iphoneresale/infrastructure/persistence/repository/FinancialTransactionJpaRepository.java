package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
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

import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionJpaRepository
        extends JpaRepository<FinancialTransaction, UUID>,
        JpaSpecificationExecutor<FinancialTransaction> {

    @EntityGraph(attributePaths = {
            "createdBy", "ownerUser", "reversalOf", "device",
            "maintenance", "maintenance.device", "sale", "sale.device"
    })
    Page<FinancialTransaction> findAll(
            Specification<FinancialTransaction> specification,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "createdBy", "ownerUser", "reversalOf", "device",
            "maintenance", "maintenance.device", "sale", "sale.device"
    })
    @Query("select transaction from FinancialTransaction transaction where transaction.id = :id")
    Optional<FinancialTransaction> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByReversalOfId(UUID reversalOfId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select original from FinancialTransaction original
             where original.type = io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType.OPENING_BALANCE
               and not exists (
                   select reversal.id from FinancialTransaction reversal
                    where reversal.reversalOf = original
               )
            """)
    Optional<FinancialTransaction> findActiveOpeningBalanceForUpdate();

    @Query("""
            select original from FinancialTransaction original
             where original.type = io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType.DEVICE_PURCHASE
               and original.device.id = :deviceId
               and not exists (
                   select reversal.id from FinancialTransaction reversal where reversal.reversalOf = original
               )
            """)
    Optional<FinancialTransaction> findActiveDevicePurchase(@Param("deviceId") UUID deviceId);

    @Query("""
            select original from FinancialTransaction original
             where original.type = io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType.MAINTENANCE
               and original.maintenance.id = :maintenanceId
               and not exists (
                   select reversal.id from FinancialTransaction reversal where reversal.reversalOf = original
               )
            """)
    Optional<FinancialTransaction> findActiveMaintenanceTransaction(
            @Param("maintenanceId") UUID maintenanceId
    );

    @Query("""
            select original from FinancialTransaction original
             where original.type = io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType.SALE
               and original.sale.id = :saleId
               and not exists (
                   select reversal.id from FinancialTransaction reversal where reversal.reversalOf = original
               )
            """)
    Optional<FinancialTransaction> findActiveSaleTransaction(@Param("saleId") UUID saleId);
}
