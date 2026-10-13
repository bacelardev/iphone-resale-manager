package io.github.bacelardev.iphoneresale.web.dto.audit;

import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.model.AuditLog;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        UserReference actorUser,
        AuditAction action,
        AuditedEntityType entityType,
        UUID entityId,
        String entityReference,
        String summary,
        Map<String, Object> changes,
        UUID requestId
) {
    public static AuditEventResponse from(AuditLog event, Map<String, Object> safeChanges) {
        return new AuditEventResponse(
                event.getId(), event.getOccurredAt(), UserReference.from(event.getActorUser()),
                event.getAction(), event.getEntityType(), event.getEntityId(),
                event.getEntityReference(), event.getSummary(), safeChanges, event.getRequestId()
        );
    }
}
