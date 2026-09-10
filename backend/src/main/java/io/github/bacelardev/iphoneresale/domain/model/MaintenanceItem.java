package io.github.bacelardev.iphoneresale.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "maintenance_item")
public class MaintenanceItem extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maintenance_id", nullable = false, updatable = false)
    private Maintenance maintenance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false, updatable = false)
    private PartCatalog part;

    @Column(name = "details", updatable = false, length = 255)
    private String details;

    @Column(name = "cost", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    protected MaintenanceItem() {
    }

    MaintenanceItem(
            Maintenance maintenance,
            PartCatalog part,
            String details,
            BigDecimal cost,
            int position
    ) {
        this.maintenance = maintenance;
        this.part = part;
        this.details = details;
        this.cost = cost;
        this.position = position;
    }

    public Maintenance getMaintenance() {
        return maintenance;
    }

    public PartCatalog getPart() {
        return part;
    }

    public String getDetails() {
        return details;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public int getPosition() {
        return position;
    }
}
