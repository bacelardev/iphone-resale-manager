package io.github.bacelardev.iphoneresale.application.service.audit;

import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.PageableFactory;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.model.AuditLog;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AuditLogJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.audit.AuditEventResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditQueryService {

    private static final Map<String, String> SORT = Map.of("occurredAt", "occurredAt");
    private static final Set<String> SENSITIVE_PARTS = Set.of(
            "password", "hash", "token", "secret", "credential", "privatekey", "storagekey"
    );

    private final AuditLogJpaRepository events;

    public AuditQueryService(AuditLogJpaRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(
            UUID userId,
            List<AuditedEntityType> entityTypes,
            UUID entityId,
            List<AuditAction> actions,
            Instant from,
            Instant to,
            int page,
            int size,
            String sort
    ) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw BusinessException.badRequest("INVALID_QUERY_PARAMETER", "O fim deve ser posterior ao início.");
        }
        Pageable pageable = PageableFactory.create(page, size, sort, SORT,
                "occurredAt", Sort.Direction.DESC);
        return PageResponse.from(events.findAll(filter(userId, entityTypes, entityId, actions, from, to), pageable),
                this::response);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse get(UUID id) {
        return response(events.findById(id).orElseThrow(() ->
                BusinessException.notFound("AUDIT_EVENT_NOT_FOUND", "Evento de auditoria não encontrado.")));
    }

    private AuditEventResponse response(AuditLog event) {
        return AuditEventResponse.from(event, sanitizeMap(event.getChanges()));
    }

    private static Specification<AuditLog> filter(
            UUID userId,
            List<AuditedEntityType> entityTypes,
            UUID entityId,
            List<AuditAction> actions,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (userId != null) predicates.add(cb.equal(root.get("actorUser").get("id"), userId));
            if (entityTypes != null && !entityTypes.isEmpty()) predicates.add(root.get("entityType").in(entityTypes));
            if (entityId != null) predicates.add(cb.equal(root.get("entityId"), entityId));
            if (actions != null && !actions.isEmpty()) predicates.add(root.get("action").in(actions));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("occurredAt"), to));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static Map<String, Object> sanitizeMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalized = key.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
            if (SENSITIVE_PARTS.stream().noneMatch(normalized::contains)) {
                result.put(key, sanitizeValue(value));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> strings = new LinkedHashMap<>();
            map.forEach((key, nested) -> strings.put(String.valueOf(key), nested));
            return sanitizeMap(strings);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(AuditQueryService::sanitizeValue).toList();
        }
        return value;
    }
}
