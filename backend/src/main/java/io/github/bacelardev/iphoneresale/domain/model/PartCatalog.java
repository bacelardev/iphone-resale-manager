package io.github.bacelardev.iphoneresale.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "part_catalog")
public class PartCatalog extends AuditableEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected PartCatalog() {
    }

    public PartCatalog(String code, String name) {
        this.code = code;
        this.name = name;
        this.active = true;
    }

    public void rename(String name) {
        this.name = name;
    }

    public boolean activate() {
        boolean changed = !active;
        active = true;
        return changed;
    }

    public boolean deactivate() {
        boolean changed = active;
        active = false;
        return changed;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
