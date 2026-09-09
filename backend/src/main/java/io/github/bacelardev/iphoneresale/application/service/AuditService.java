package io.github.bacelardev.iphoneresale.application.service;

import io.github.bacelardev.iphoneresale.application.port.security.CurrentUserIdProvider;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.domain.model.AuditLog;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AppUserJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AuditLogJpaRepository;
import io.github.bacelardev.iphoneresale.web.filter.RequestIdFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogJpaRepository auditLogs;
    private final AppUserJpaRepository users;
    private final CurrentUserIdProvider currentUser;
    private final Clock clock;

    public AuditService(
            AuditLogJpaRepository auditLogs,
            AppUserJpaRepository users,
            CurrentUserIdProvider currentUser,
            Clock clock
    ) {
        this.auditLogs = auditLogs;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public AppUser actor() {
        UUID id = currentUser.currentUserId().orElseThrow(() ->
                BusinessException.badRequest("UNAUTHORIZED", "Usuário autenticado não encontrado."));
        return users.getReferenceById(id);
    }

    public void record(
            AuditAction action,
            AuditedEntityType entityType,
            UUID entityId,
            String reference,
            String summary,
            Map<String, Object> changes
    ) {
        auditLogs.save(new AuditLog(
                clock.instant(),
                actor(),
                action,
                entityType,
                entityId,
                reference,
                summary,
                changes,
                requestId()
        ));
    }

    private UUID requestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null
                : attributes.getAttribute(RequestIdFilter.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
        return value instanceof UUID uuid ? uuid : null;
    }
}
