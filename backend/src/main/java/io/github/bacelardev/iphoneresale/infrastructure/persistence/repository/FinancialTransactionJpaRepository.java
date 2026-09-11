package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionJpaRepository
        extends JpaRepository<FinancialTransaction, UUID> {

    @Query("""
            select original
              from FinancialTransaction original
             where original.type = io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType.DEVICE_PURCHASE
               and original.device.id = :deviceId
               and not exists (
                   select reversal.id
                     from FinancialTransaction reversal
                    where reversal.reversalOf = original
               )
            """)
    Optional<FinancialTransaction> findActiveDevicePurchase(@Param("deviceId") UUID deviceId);

    @Query("""
            select original
              from FinancialTransaction original
             where original.type = io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType.MAINTENANCE
               and original.maintenance.id = :maintenanceId
               and not exists (
                   select reversal.id
                     from FinancialTransaction reversal
                    where reversal.reversalOf = original
               )
            """)
    Optional<FinancialTransaction> findActiveMaintenanceTransaction(
            @Param("maintenanceId") UUID maintenanceId
    );

    @Query("""
            select original
              from FinancialTransaction original
             where original.type = io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType.SALE
               and original.sale.id = :saleId
               and not exists (
                   select reversal.id
                     from FinancialTransaction reversal
                    where reversal.reversalOf = original
               )
            """)
    Optional<FinancialTransaction> findActiveSaleTransaction(@Param("saleId") UUID saleId);
}
