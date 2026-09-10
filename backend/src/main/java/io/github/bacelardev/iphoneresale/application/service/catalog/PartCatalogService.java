package io.github.bacelardev.iphoneresale.application.service.catalog;

import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.PageableFactory;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.model.PartCatalog;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.PartCatalogJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.catalog.CreatePartRequest;
import io.github.bacelardev.iphoneresale.web.dto.catalog.PartCatalogResponse;
import io.github.bacelardev.iphoneresale.web.dto.catalog.UpdatePartRequest;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PartCatalogService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]+$");
    private static final Map<String, String> SORT = Map.of(
            "name", "name", "code", "code", "createdAt", "createdAt"
    );

    private final PartCatalogJpaRepository parts;
    private final AuditService audit;

    public PartCatalogService(PartCatalogJpaRepository parts, AuditService audit) {
        this.parts = parts;
        this.audit = audit;
    }

    @Transactional
    public PartCatalogResponse create(CreatePartRequest request) {
        String code = normalizeCode(request.code());
        String name = cleanName(request.name());
        if (parts.existsByCode(code)) duplicateCode();
        if (parts.existsByNameIgnoreCase(name)) duplicateName();
        PartCatalog part = parts.saveAndFlush(new PartCatalog(code, name));
        audit.record(AuditAction.CREATED, AuditedEntityType.PART_CATALOG, part.getId(), code,
                "Peça " + name + " criada.", Map.of("code", code, "name", name));
        return PartCatalogResponse.from(part);
    }

    @Transactional(readOnly = true)
    public PartCatalogResponse get(UUID id) {
        return PartCatalogResponse.from(part(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PartCatalogResponse> list(
            String search, Boolean active, int page, int size, String sort
    ) {
        return PageResponse.from(parts.findAll(filter(search, active), PageableFactory.create(
                page, size, sort, SORT, "name", Sort.Direction.ASC)), PartCatalogResponse::from);
    }

    @Transactional
    public PartCatalogResponse update(UUID id, UpdatePartRequest request) {
        PartCatalog part = part(id);
        VersionGuard.require(part.getVersion(), request.expectedVersion());
        String name = cleanName(request.name());
        if (parts.existsByNameIgnoreCaseAndIdNot(name, id)) duplicateName();
        part.rename(name);
        parts.flush();
        audit.record(AuditAction.UPDATED, AuditedEntityType.PART_CATALOG, id, part.getCode(),
                "Peça " + name + " atualizada.", Map.of("name", name));
        return PartCatalogResponse.from(part);
    }

    @Transactional
    public PartCatalogResponse setActive(UUID id, long expectedVersion, boolean active) {
        PartCatalog part = part(id);
        VersionGuard.require(part.getVersion(), expectedVersion);
        boolean changed = active ? part.activate() : part.deactivate();
        if (changed) parts.flush();
        if (changed) {
            audit.record(active ? AuditAction.ACTIVATED : AuditAction.DEACTIVATED,
                    AuditedEntityType.PART_CATALOG, id, part.getCode(),
                    "Peça " + part.getName() + (active ? " ativada." : " desativada."),
                    Map.of("active", active));
        }
        return PartCatalogResponse.from(part);
    }

    private PartCatalog part(UUID id) {
        return parts.findById(id).orElseThrow(() ->
                BusinessException.notFound("PART_NOT_FOUND", "Peça não encontrada."));
    }

    private static String normalizeCode(String raw) {
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Código de peça inválido.");
        }
        return code;
    }

    private static String cleanName(String raw) {
        if (!raw.equals(raw.trim())) {
            throw BusinessException.badRequest("VALIDATION_ERROR",
                    "O nome não pode possuir espaços externos.");
        }
        return raw;
    }

    private static void duplicateCode() {
        throw BusinessException.conflict("CATALOG_CODE_ALREADY_EXISTS",
                "Já existe um item com esse código.");
    }

    private static void duplicateName() {
        throw BusinessException.conflict("CATALOG_NAME_ALREADY_EXISTS",
                "Já existe um item com esse nome.");
    }

    private static Specification<PartCatalog> filter(String search, Boolean active) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)));
            }
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
