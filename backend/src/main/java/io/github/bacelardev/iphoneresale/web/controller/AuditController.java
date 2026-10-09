package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.audit.AuditQueryService;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.web.dto.audit.AuditEventResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(name = "entityType", required = false) List<AuditedEntityType> entityTypes,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(name = "action", required = false) List<AuditAction> actions,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.list(userId, entityTypes, entityId, actions, from, to, page, size, sort);
    }

    @GetMapping("/{id}")
    public AuditEventResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
