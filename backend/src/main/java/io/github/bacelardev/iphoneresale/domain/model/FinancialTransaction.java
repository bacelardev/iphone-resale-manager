package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.FinancialDirection;
import io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "financial_transaction")
public class FinancialTransaction extends CreatedOnlyEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 10)
    private FinancialDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private FinancialTransactionType type;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", updatable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_id", updatable = false)
    private Maintenance maintenance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", updatable = false)
    private Sale sale;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id", updatable = false)
    private FinancialTransaction reversalOf;

    @Column(name = "description", updatable = false, length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", updatable = false)
    private AppUser ownerUser;

    protected FinancialTransaction() {
    }

    public static FinancialTransaction devicePurchase(Device device) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.direction = FinancialDirection.OUTFLOW;
        transaction.type = FinancialTransactionType.DEVICE_PURCHASE;
        transaction.amount = device.getPurchasePrice();
        transaction.occurredAt = device.getPurchasedAt();
        transaction.device = device;
        return transaction;
    }

    public static FinancialTransaction devicePurchaseReversal(
            FinancialTransaction original,
            String description
    ) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.direction = FinancialDirection.INFLOW;
        transaction.type = FinancialTransactionType.DEVICE_PURCHASE_REVERSAL;
        transaction.amount = original.amount;
        transaction.occurredAt = original.occurredAt;
        transaction.device = original.device;
        transaction.reversalOf = original;
        transaction.description = description;
        return transaction;
    }

    public static FinancialTransaction maintenance(Maintenance maintenance, BigDecimal amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.direction = FinancialDirection.OUTFLOW;
        transaction.type = FinancialTransactionType.MAINTENANCE;
        transaction.amount = amount;
        transaction.occurredAt = maintenance.getPerformedAt();
        transaction.maintenance = maintenance;
        return transaction;
    }

    public static FinancialTransaction maintenanceReversal(
            FinancialTransaction original,
            Instant occurredAt,
            String description
    ) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.direction = FinancialDirection.INFLOW;
        transaction.type = FinancialTransactionType.MAINTENANCE_REVERSAL;
        transaction.amount = original.amount;
        transaction.occurredAt = occurredAt;
        transaction.maintenance = original.maintenance;
        transaction.reversalOf = original;
        transaction.description = description;
        return transaction;
    }

    public static FinancialTransaction sale(Sale sale) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.direction = FinancialDirection.INFLOW;
        transaction.type = FinancialTransactionType.SALE;
        transaction.amount = sale.getSalePrice();
        transaction.occurredAt = sale.getSoldAt();
        transaction.sale = sale;
        return transaction;
    }

    public static FinancialTransaction saleReversal(
            FinancialTransaction original,
            Instant occurredAt,
            String description
    ) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.direction = FinancialDirection.OUTFLOW;
        transaction.type = FinancialTransactionType.SALE_REVERSAL;
        transaction.amount = original.amount;
        transaction.occurredAt = occurredAt;
        transaction.sale = original.sale;
        transaction.reversalOf = original;
        transaction.description = description;
        return transaction;
    }

    public static FinancialTransaction openingBalance(BigDecimal amount, Instant occurredAt) {
        return manual(FinancialDirection.INFLOW, FinancialTransactionType.OPENING_BALANCE,
                null, amount, occurredAt, "Saldo inicial declarado na conclusão da implantação.", null);
    }

    public static FinancialTransaction ownerContribution(
            AppUser ownerUser, BigDecimal amount, Instant occurredAt, String description
    ) {
        return manual(FinancialDirection.INFLOW, FinancialTransactionType.OWNER_CONTRIBUTION,
                ownerUser, amount, occurredAt, description, null);
    }

    public static FinancialTransaction ownerWithdrawal(
            AppUser ownerUser, BigDecimal amount, Instant occurredAt, String description
    ) {
        return manual(FinancialDirection.OUTFLOW, FinancialTransactionType.OWNER_WITHDRAWAL,
                ownerUser, amount, occurredAt, description, null);
    }

    public static FinancialTransaction manualAdjustment(
            FinancialDirection direction, BigDecimal amount, Instant occurredAt, String description
    ) {
        return manual(direction, FinancialTransactionType.MANUAL_ADJUSTMENT,
                null, amount, occurredAt, description, null);
    }

    public static FinancialTransaction manualReversal(
            FinancialTransaction original, Instant occurredAt, String description
    ) {
        Objects.requireNonNull(original, "original");
        FinancialDirection opposite = original.direction == FinancialDirection.INFLOW
                ? FinancialDirection.OUTFLOW : FinancialDirection.INFLOW;
        return manual(opposite, FinancialTransactionType.MANUAL_ADJUSTMENT,
                null, original.amount, occurredAt, description, original);
    }

    private static FinancialTransaction manual(
            FinancialDirection direction,
            FinancialTransactionType type,
            AppUser ownerUser,
            BigDecimal amount,
            Instant occurredAt,
            String description,
            FinancialTransaction reversalOf
    ) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.direction = Objects.requireNonNull(direction, "direction");
        transaction.type = Objects.requireNonNull(type, "type");
        transaction.ownerUser = ownerUser;
        transaction.amount = Objects.requireNonNull(amount, "amount");
        transaction.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        transaction.description = Objects.requireNonNull(description, "description").trim();
        transaction.reversalOf = reversalOf;
        return transaction;
    }

    public FinancialDirection getDirection() {
        return direction;
    }

    public FinancialTransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Device getDevice() {
        return device;
    }

    public Maintenance getMaintenance() {
        return maintenance;
    }

    public Sale getSale() {
        return sale;
    }

    public FinancialTransaction getReversalOf() {
        return reversalOf;
    }

    public String getDescription() {
        return description;
    }

    public AppUser getOwnerUser() {
        return ownerUser;
    }
}
