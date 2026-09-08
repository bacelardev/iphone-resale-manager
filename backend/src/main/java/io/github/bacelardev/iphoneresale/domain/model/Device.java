package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "device")
public class Device extends AuditableEntity {

    @Generated(event = EventType.INSERT)
    @ColumnDefault("'IPH-' || lpad(nextval('device_internal_code_seq')::text, 6, '0')")
    @Column(name = "internal_code", nullable = false, unique = true, updatable = false, insertable = false, length = 24)
    private String internalCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private IphoneModel model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id", nullable = false)
    private DeviceColor color;

    @Column(name = "storage_gb", nullable = false)
    private int storageGb;

    @Column(name = "purchase_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    @Column(name = "face_id_working", nullable = false)
    private boolean faceIdWorking;

    @Column(name = "original_screen", nullable = false)
    private boolean originalScreen;

    @Column(name = "original_battery", nullable = false)
    private boolean originalBattery;

    @Column(name = "battery_health_percent", nullable = false)
    private int batteryHealthPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeviceStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by")
    private AppUser archivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_origin", nullable = false, updatable = false, length = 24)
    private RegistrationOrigin registrationOrigin;

    protected Device() {
    }

    public Device(
            IphoneModel model,
            DeviceColor color,
            int storageGb,
            BigDecimal purchasePrice,
            Instant purchasedAt,
            boolean faceIdWorking,
            boolean originalScreen,
            boolean originalBattery,
            int batteryHealthPercent,
            DeviceStatus status,
            RegistrationOrigin registrationOrigin
    ) {
        this.model = model;
        this.color = color;
        this.storageGb = storageGb;
        this.purchasePrice = purchasePrice;
        this.purchasedAt = purchasedAt;
        this.faceIdWorking = faceIdWorking;
        this.originalScreen = originalScreen;
        this.originalBattery = originalBattery;
        this.batteryHealthPercent = batteryHealthPercent;
        this.status = status;
        this.registrationOrigin = registrationOrigin;
    }

    public void update(
            IphoneModel model,
            DeviceColor color,
            int storageGb,
            BigDecimal purchasePrice,
            Instant purchasedAt,
            boolean faceIdWorking,
            boolean originalScreen,
            boolean originalBattery,
            int batteryHealthPercent
    ) {
        this.model = model;
        this.color = color;
        this.storageGb = storageGb;
        this.purchasePrice = purchasePrice;
        this.purchasedAt = purchasedAt;
        this.faceIdWorking = faceIdWorking;
        this.originalScreen = originalScreen;
        this.originalBattery = originalBattery;
        this.batteryHealthPercent = batteryHealthPercent;
    }

    public void changeStatus(DeviceStatus status) {
        this.status = status;
    }

    public void archive(Instant archivedAt, AppUser archivedBy) {
        this.archivedAt = archivedAt;
        this.archivedBy = archivedBy;
    }

    public String getInternalCode() {
        return internalCode;
    }

    public IphoneModel getModel() {
        return model;
    }

    public DeviceColor getColor() {
        return color;
    }

    public int getStorageGb() {
        return storageGb;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public boolean isFaceIdWorking() {
        return faceIdWorking;
    }

    public boolean isOriginalScreen() {
        return originalScreen;
    }

    public boolean isOriginalBattery() {
        return originalBattery;
    }

    /**
     * Returns 0 when battery health was unavailable/not measured at registration;
     * values from 1 to 100 are actual reported percentages.
     */
    public int getBatteryHealthPercent() {
        return batteryHealthPercent;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public AppUser getArchivedBy() {
        return archivedBy;
    }

    public RegistrationOrigin getRegistrationOrigin() {
        return registrationOrigin;
    }
}
