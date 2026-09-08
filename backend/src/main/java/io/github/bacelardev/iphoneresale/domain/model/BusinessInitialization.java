package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.BusinessInitializationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "business_initialization")
public class BusinessInitialization extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BusinessInitializationStatus status;

    @Column(name = "cutoff_at", nullable = false)
    private Instant cutoffAt;

    @Column(name = "declared_cash_balance", precision = 14, scale = 2)
    private BigDecimal declaredCashBalance;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opening_balance_transaction_id")
    private FinancialTransaction openingBalanceTransaction;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private AppUser completedBy;

    protected BusinessInitialization() {
    }

    public BusinessInitialization(Instant cutoffAt) {
        this.status = BusinessInitializationStatus.PREPARING;
        this.cutoffAt = cutoffAt;
    }

    public void updateCutoff(Instant cutoffAt) {
        this.cutoffAt = cutoffAt;
    }

    public BusinessInitializationStatus getStatus() {
        return status;
    }

    public Instant getCutoffAt() {
        return cutoffAt;
    }

    public BigDecimal getDeclaredCashBalance() {
        return declaredCashBalance;
    }

    public FinancialTransaction getOpeningBalanceTransaction() {
        return openingBalanceTransaction;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public AppUser getCompletedBy() {
        return completedBy;
    }
}
