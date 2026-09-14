package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.SaleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sale")
public class Sale extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, updatable = false)
    private Device device;

    @Column(name = "sale_price", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sold_at", nullable = false, updatable = false)
    private Instant soldAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsible_user_id", nullable = false, updatable = false)
    private AppUser responsibleUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUser cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected Sale() {
    }

    public static Sale active(
            Device device,
            BigDecimal salePrice,
            Instant soldAt,
            AppUser responsibleUser
    ) {
        Sale sale = new Sale();
        sale.device = Objects.requireNonNull(device, "device");
        Objects.requireNonNull(salePrice, "salePrice");
        if (salePrice.signum() <= 0 || salePrice.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Sale price must be positive with at most two decimals");
        }
        sale.salePrice = salePrice;
        sale.soldAt = Objects.requireNonNull(soldAt, "soldAt");
        sale.responsibleUser = Objects.requireNonNull(responsibleUser, "responsibleUser");
        sale.status = SaleStatus.ACTIVE;
        return sale;
    }

    public void cancel(Instant cancelledAt, AppUser cancelledBy, String cancellationReason) {
        if (status != SaleStatus.ACTIVE) {
            throw new IllegalStateException("Only an active sale can be cancelled");
        }
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        Objects.requireNonNull(cancelledBy, "cancelledBy");
        String reason = Objects.requireNonNull(cancellationReason, "cancellationReason").trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new IllegalArgumentException("Cancellation reason must have 1 to 500 characters");
        }
        this.status = SaleStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = reason;
    }

    public Device getDevice() {
        return device;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public Instant getSoldAt() {
        return soldAt;
    }

    public AppUser getResponsibleUser() {
        return responsibleUser;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public AppUser getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
