package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog extends BaseUuidEntity {

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private AppUser actorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private AuditedEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID entityId;

    @Column(name = "entity_reference", updatable = false, length = 120)
    private String entityReference;

    @Column(name = "summary", nullable = false, updatable = false, length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> changes;

    @Column(name = "request_id", updatable = false, columnDefinition = "uuid")
    private UUID requestId;

    protected AuditLog() {
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AppUser getActorUser() {
        return actorUser;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditedEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getEntityReference() {
        return entityReference;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getChanges() {
        return changes;
    }

    public UUID getRequestId() {
        return requestId;
    }
}
