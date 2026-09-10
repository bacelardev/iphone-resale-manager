package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "maintenance")
public class Maintenance extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, updatable = false)
    private Device device;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsible_user_id", nullable = false, updatable = false)
    private AppUser responsibleUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MaintenanceStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUser cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_origin", nullable = false, updatable = false, length = 24)
    private RegistrationOrigin registrationOrigin;

    @OneToMany(mappedBy = "maintenance", cascade = CascadeType.PERSIST, orphanRemoval = false)
    @OrderBy("position ASC")
    private List<MaintenanceItem> items = new ArrayList<>();

    protected Maintenance() {
    }

    public Maintenance(
            Device device,
            Instant performedAt,
            AppUser responsibleUser,
            RegistrationOrigin registrationOrigin
    ) {
        this.device = device;
        this.performedAt = performedAt;
        this.responsibleUser = responsibleUser;
        this.status = MaintenanceStatus.ACTIVE;
        this.registrationOrigin = registrationOrigin;
    }

    public void addItem(PartCatalog part, String details, BigDecimal cost, int position) {
        items.add(new MaintenanceItem(this, part, details, cost, position));
    }

    public BigDecimal total() {
        return items.stream()
                .map(MaintenanceItem::getCost)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public void cancel(Instant at, AppUser actor, String reason) {
        this.status = MaintenanceStatus.CANCELLED;
        this.cancelledAt = at;
        this.cancelledBy = actor;
        this.cancellationReason = reason;
    }

    public Device getDevice() {
        return device;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public AppUser getResponsibleUser() {
        return responsibleUser;
    }

    public MaintenanceStatus getStatus() {
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

    public RegistrationOrigin getRegistrationOrigin() {
        return registrationOrigin;
    }

    public List<MaintenanceItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
