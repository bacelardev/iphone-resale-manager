package io.github.bacelardev.iphoneresale.application.service.catalog;

import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.PageableFactory;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.model.DeviceColor;
import io.github.bacelardev.iphoneresale.domain.model.IphoneModel;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DeviceColorJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.IphoneModelJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.catalog.CreateCatalogRequest;
import io.github.bacelardev.iphoneresale.web.dto.catalog.CreateModelRequest;
import io.github.bacelardev.iphoneresale.web.dto.catalog.DeviceColorResponse;
import io.github.bacelardev.iphoneresale.web.dto.catalog.IphoneModelResponse;
import io.github.bacelardev.iphoneresale.web.dto.catalog.UpdateCatalogRequest;
import io.github.bacelardev.iphoneresale.web.dto.catalog.UpdateModelRequest;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CatalogService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]+$");
    private static final Map<String, String> MODEL_SORT = Map.of(
            "name", "name", "code", "code", "displayOrder", "displayOrder",
            "createdAt", "createdAt"
    );
    private static final Map<String, String> COLOR_SORT = Map.of(
            "name", "name", "code", "code", "createdAt", "createdAt"
    );

    private final IphoneModelJpaRepository models;
    private final DeviceColorJpaRepository colors;
    private final AuditService audit;

    public CatalogService(
            IphoneModelJpaRepository models,
            DeviceColorJpaRepository colors,
            AuditService audit
    ) {
        this.models = models;
        this.colors = colors;
        this.audit = audit;
    }

    @Transactional
    public IphoneModelResponse createModel(CreateModelRequest request) {
        String code = normalizeCode(request.code());
        String name = cleanName(request.name());
        ensureModelUnique(code, name, null);
        IphoneModel model = models.saveAndFlush(new IphoneModel(code, name, request.displayOrder()));
        audit.record(AuditAction.CREATED, AuditedEntityType.IPHONE_MODEL, model.getId(), code,
                "Modelo " + name + " criado.", Map.of("code", code, "name", name));
        return IphoneModelResponse.from(model);
    }

    @Transactional(readOnly = true)
    public IphoneModelResponse getModel(UUID id) {
        return IphoneModelResponse.from(model(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<IphoneModelResponse> listModels(
            String search, Boolean active, int page, int size, String sort
    ) {
        Pageable pageable = PageableFactory.create(page, size, sort, MODEL_SORT,
                "displayOrder", Sort.Direction.ASC);
        return PageResponse.from(models.findAll(modelFilter(search, active), pageable),
                IphoneModelResponse::from);
    }

    @Transactional
    public IphoneModelResponse updateModel(UUID id, UpdateModelRequest request) {
        IphoneModel model = model(id);
        VersionGuard.require(model.getVersion(), request.expectedVersion());
        String name = cleanName(request.name());
        ensureModelUnique(model.getCode(), name, id);
        model.update(name, request.displayOrder());
        models.flush();
        audit.record(AuditAction.UPDATED, AuditedEntityType.IPHONE_MODEL, id, model.getCode(),
                "Modelo " + name + " atualizado.", Map.of("name", name));
        return IphoneModelResponse.from(model);
    }

    @Transactional
    public IphoneModelResponse setModelActive(UUID id, long expectedVersion, boolean active) {
        IphoneModel model = model(id);
        VersionGuard.require(model.getVersion(), expectedVersion);
        boolean changed = active ? model.activate() : model.deactivate();
        if (changed) models.flush();
        if (changed) {
            audit.record(active ? AuditAction.ACTIVATED : AuditAction.DEACTIVATED,
                    AuditedEntityType.IPHONE_MODEL, id, model.getCode(),
                    "Modelo " + model.getName() + (active ? " ativado." : " desativado."),
                    Map.of("active", active));
        }
        return IphoneModelResponse.from(model);
    }

    @Transactional
    public DeviceColorResponse createColor(CreateCatalogRequest request) {
        String code = normalizeCode(request.code());
        String name = cleanName(request.name());
        ensureColorUnique(code, name, null);
        DeviceColor color = colors.saveAndFlush(new DeviceColor(code, name));
        audit.record(AuditAction.CREATED, AuditedEntityType.DEVICE_COLOR, color.getId(), code,
                "Cor " + name + " criada.", Map.of("code", code, "name", name));
        return DeviceColorResponse.from(color);
    }

    @Transactional(readOnly = true)
    public DeviceColorResponse getColor(UUID id) {
        return DeviceColorResponse.from(color(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceColorResponse> listColors(
            String search, Boolean active, int page, int size, String sort
    ) {
        Pageable pageable = PageableFactory.create(page, size, sort, COLOR_SORT,
                "name", Sort.Direction.ASC);
        return PageResponse.from(colors.findAll(colorFilter(search, active), pageable),
                DeviceColorResponse::from);
    }

    @Transactional
    public DeviceColorResponse updateColor(UUID id, UpdateCatalogRequest request) {
        DeviceColor color = color(id);
        VersionGuard.require(color.getVersion(), request.expectedVersion());
        String name = cleanName(request.name());
        ensureColorUnique(color.getCode(), name, id);
        color.rename(name);
        colors.flush();
        audit.record(AuditAction.UPDATED, AuditedEntityType.DEVICE_COLOR, id, color.getCode(),
                "Cor " + name + " atualizada.", Map.of("name", name));
        return DeviceColorResponse.from(color);
    }

    @Transactional
    public DeviceColorResponse setColorActive(UUID id, long expectedVersion, boolean active) {
        DeviceColor color = color(id);
        VersionGuard.require(color.getVersion(), expectedVersion);
        boolean changed = active ? color.activate() : color.deactivate();
        if (changed) colors.flush();
        if (changed) {
            audit.record(active ? AuditAction.ACTIVATED : AuditAction.DEACTIVATED,
                    AuditedEntityType.DEVICE_COLOR, id, color.getCode(),
                    "Cor " + color.getName() + (active ? " ativada." : " desativada."),
                    Map.of("active", active));
        }
        return DeviceColorResponse.from(color);
    }

    private IphoneModel model(UUID id) {
        return models.findById(id).orElseThrow(() ->
                BusinessException.notFound("MODEL_NOT_FOUND", "Modelo não encontrado."));
    }

    private DeviceColor color(UUID id) {
        return colors.findById(id).orElseThrow(() ->
                BusinessException.notFound("COLOR_NOT_FOUND", "Cor não encontrada."));
    }

    private void ensureModelUnique(String code, String name, UUID id) {
        if (id == null && models.existsByCode(code)) duplicateCode();
        if (id == null ? models.existsByNameIgnoreCase(name)
                : models.existsByNameIgnoreCaseAndIdNot(name, id)) duplicateName();
    }

    private void ensureColorUnique(String code, String name, UUID id) {
        if (id == null && colors.existsByCode(code)) duplicateCode();
        if (id == null ? colors.existsByNameIgnoreCase(name)
                : colors.existsByNameIgnoreCaseAndIdNot(name, id)) duplicateName();
    }

    private static void duplicateCode() {
        throw BusinessException.conflict("CATALOG_CODE_ALREADY_EXISTS",
                "Já existe um item com esse código.");
    }

    private static void duplicateName() {
        throw BusinessException.conflict("CATALOG_NAME_ALREADY_EXISTS",
                "Já existe um item com esse nome.");
    }

    private static String normalizeCode(String raw) {
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Código de catálogo inválido.");
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

    private static Specification<IphoneModel> modelFilter(String search, Boolean active) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like)));
            }
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static Specification<DeviceColor> colorFilter(String search, Boolean active) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
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
