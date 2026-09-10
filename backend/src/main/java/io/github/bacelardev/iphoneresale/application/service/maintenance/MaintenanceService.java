package io.github.bacelardev.iphoneresale.application.service.maintenance;

import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.PageableFactory;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.enums.BusinessInitializationStatus;
import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import io.github.bacelardev.iphoneresale.domain.model.Device;
import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
import io.github.bacelardev.iphoneresale.domain.model.Maintenance;
import io.github.bacelardev.iphoneresale.domain.model.PartCatalog;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.BusinessInitializationJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DeviceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.FinancialTransactionJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.MaintenanceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.PartCatalogJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.CancelMaintenanceRequest;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.MaintenanceDetailResponse;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.MaintenanceItemRequest;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.MaintenanceSummaryResponse;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.RegisterMaintenanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MaintenanceService {

    private static final Map<String, String> SORT = Map.of(
            "performedAt", "performedAt", "createdAt", "createdAt", "status", "status"
    );

    private final DeviceJpaRepository devices;
    private final MaintenanceJpaRepository maintenances;
    private final PartCatalogJpaRepository parts;
    private final FinancialTransactionJpaRepository transactions;
    private final BusinessInitializationJpaRepository initializations;
    private final AuditService audit;
    private final Clock clock;

    public MaintenanceService(
            DeviceJpaRepository devices,
            MaintenanceJpaRepository maintenances,
            PartCatalogJpaRepository parts,
            FinancialTransactionJpaRepository transactions,
            BusinessInitializationJpaRepository initializations,
            AuditService audit,
            Clock clock
    ) {
        this.devices = devices;
        this.maintenances = maintenances;
        this.parts = parts;
        this.transactions = transactions;
        this.initializations = initializations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceDetailResponse registerOperational(
            UUID deviceId,
            RegisterMaintenanceRequest request
    ) {
        return register(deviceId, request, RegistrationOrigin.OPERATIONAL);
    }

    @Transactional
    public MaintenanceDetailResponse registerInitialImport(
            UUID deviceId,
            RegisterMaintenanceRequest request
    ) {
        return register(deviceId, request, RegistrationOrigin.INITIAL_IMPORT);
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceSummaryResponse> list(
            UUID deviceId,
            MaintenanceStatus status,
            Instant from,
            Instant to,
            int page,
            int size,
            String sort
    ) {
        requireDevice(deviceId);
        if (from != null && to != null && !from.isBefore(to)) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "from deve ser anterior a to.");
        }
        Pageable pageable = PageableFactory.create(page, size, sort, SORT,
                "performedAt", Sort.Direction.DESC);
        Page<Maintenance> result = maintenances.findAll(filter(deviceId, status, from, to), pageable);
        Map<UUID, BigDecimal> totals = totals(result.getContent());
        return PageResponse.from(result, maintenance -> MaintenanceSummaryResponse.from(
                maintenance, totals.getOrDefault(maintenance.getId(), moneyZero())));
    }

    @Transactional(readOnly = true)
    public MaintenanceDetailResponse get(UUID deviceId, UUID maintenanceId) {
        requireDevice(deviceId);
        return MaintenanceDetailResponse.from(detail(deviceId, maintenanceId));
    }

    @Transactional
    public MaintenanceDetailResponse cancel(
            UUID deviceId,
            UUID maintenanceId,
            CancelMaintenanceRequest request
    ) {
        Device device = lockedDevice(deviceId);
        ensureNoActiveSale(device);
        Maintenance maintenance = maintenances.findByIdForUpdate(deviceId, maintenanceId)
                .orElseThrow(MaintenanceService::maintenanceNotFound);
        VersionGuard.require(maintenance.getVersion(), request.expectedVersion());
        if (maintenance.getStatus() != MaintenanceStatus.ACTIVE) {
            throw BusinessException.conflict("MAINTENANCE_ALREADY_CANCELLED",
                    "A manutenção já foi cancelada.");
        }
        String reason = cleanReason(request.reason());
        Instant cancelledAt = clock.instant();
        AppUser actor = audit.actor();
        BigDecimal total = maintenance.total();
        UUID reversalTransactionId = reverseIfNecessary(maintenance, total, cancelledAt, reason);
        maintenance.cancel(cancelledAt, actor, reason);
        maintenances.flush();
        Map<String, Object> changes = auditChanges(maintenance, total);
        changes.put("reason", reason);
        if (reversalTransactionId != null) {
            changes.put("reversalTransactionId", reversalTransactionId.toString());
        }
        audit.record(AuditAction.MAINTENANCE_CANCELLED, AuditedEntityType.MAINTENANCE,
                maintenance.getId(), device.getInternalCode(),
                "Manutenção do aparelho " + device.getInternalCode() + " cancelada.", changes);
        return MaintenanceDetailResponse.from(maintenance);
    }

    @Transactional
    public ArchiveMaintenanceResult cancelActiveForArchive(Device lockedDevice, String reason) {
        List<Maintenance> active = maintenances.findByDeviceAndStatusForUpdate(
                lockedDevice.getId(), MaintenanceStatus.ACTIVE);
        if (active.isEmpty()) return new ArchiveMaintenanceResult(0, moneyZero());
        Map<UUID, BigDecimal> totals = totals(active);
        Instant cancelledAt = clock.instant();
        AppUser actor = audit.actor();
        BigDecimal reversed = moneyZero();
        for (Maintenance maintenance : active) {
            BigDecimal total = totals.getOrDefault(maintenance.getId(), moneyZero());
            if (reverseIfNecessary(maintenance, total, cancelledAt, reason) != null) {
                reversed = reversed.add(total);
            }
            maintenance.cancel(cancelledAt, actor, reason);
        }
        maintenances.flush();
        return new ArchiveMaintenanceResult(active.size(), reversed);
    }

    private MaintenanceDetailResponse register(
            UUID deviceId,
            RegisterMaintenanceRequest request,
            RegistrationOrigin origin
    ) {
        Device device = lockedDevice(deviceId);
        ensureMaintainable(device);
        Optional<BusinessInitialization> initialization = initializations.findSingletonForUpdate();
        validateWindow(device, request.performedAt(), origin, initialization);
        Map<UUID, PartCatalog> availableParts = loadParts(request.items());
        Maintenance maintenance = new Maintenance(device, request.performedAt(), audit.actor(), origin);
        for (int index = 0; index < request.items().size(); index++) {
            MaintenanceItemRequest item = request.items().get(index);
            PartCatalog part = availableParts.get(item.partId());
            maintenance.addItem(part, cleanDetails(part, item.details()), item.cost(), index + 1);
        }
        maintenances.saveAndFlush(maintenance);
        BigDecimal total = maintenance.total();
        FinancialTransaction transaction = origin == RegistrationOrigin.OPERATIONAL
                && total.signum() > 0
                ? transactions.saveAndFlush(FinancialTransaction.maintenance(maintenance, total))
                : null;
        Map<String, Object> changes = auditChanges(maintenance, total);
        if (transaction != null) changes.put("transactionId", transaction.getId().toString());
        audit.record(AuditAction.MAINTENANCE_REGISTERED, AuditedEntityType.MAINTENANCE,
                maintenance.getId(), device.getInternalCode(),
                origin == RegistrationOrigin.INITIAL_IMPORT
                        ? "Manutenção histórica importada para o aparelho " + device.getInternalCode() + "."
                        : "Manutenção registrada para o aparelho " + device.getInternalCode() + ".",
                changes);
        return MaintenanceDetailResponse.from(maintenance);
    }

    private Map<UUID, PartCatalog> loadParts(List<MaintenanceItemRequest> items) {
        Map<UUID, PartCatalog> found = new LinkedHashMap<>();
        parts.findAllById(items.stream().map(MaintenanceItemRequest::partId).distinct().toList())
                .forEach(part -> found.put(part.getId(), part));
        for (MaintenanceItemRequest item : items) {
            PartCatalog part = found.get(item.partId());
            if (part == null) {
                throw BusinessException.notFound("PART_NOT_FOUND", "Peça não encontrada.");
            }
            if (!part.isActive()) {
                throw BusinessException.unprocessable("CATALOG_ITEM_INACTIVE",
                        "A peça selecionada está inativa.");
            }
        }
        return found;
    }

    private static String cleanDetails(PartCatalog part, String raw) {
        String details = raw == null || raw.isBlank() ? null : raw.trim();
        if ("OTHER".equals(part.getCode()) && details == null) {
            throw BusinessException.unprocessable("DETAILS_REQUIRED_FOR_OTHER",
                    "Detalhes são obrigatórios para a peça OTHER.");
        }
        if (details != null && details.length() > 255) {
            throw BusinessException.badRequest("VALIDATION_ERROR",
                    "Os detalhes devem possuir no máximo 255 caracteres após trim.");
        }
        return details;
    }

    private static String cleanReason(String raw) {
        String reason = raw.trim();
        if (reason.isEmpty()) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Informe o motivo do cancelamento.");
        }
        return reason;
    }

    private void validateWindow(
            Device device,
            Instant performedAt,
            RegistrationOrigin origin,
            Optional<BusinessInitialization> initialization
    ) {
        if (origin == RegistrationOrigin.INITIAL_IMPORT) {
            BusinessInitialization value = initialization.orElseThrow(() ->
                    BusinessException.conflict("BUSINESS_INITIALIZATION_NOT_STARTED",
                            "Inicie a preparação antes de importar manutenções históricas."));
            if (value.getStatus() != BusinessInitializationStatus.PREPARING) {
                throw BusinessException.conflict("INITIAL_MAINTENANCE_IMPORT_CLOSED",
                        "A importação histórica não está mais disponível.");
            }
            if (device.getRegistrationOrigin() != RegistrationOrigin.INITIAL_IMPORT) {
                throw BusinessException.unprocessable("INITIAL_MAINTENANCE_REQUIRES_IMPORTED_DEVICE",
                        "A manutenção histórica exige um aparelho do estoque inicial.");
            }
            if (performedAt.isBefore(device.getPurchasedAt())
                    || performedAt.isAfter(value.getCutoffAt())) {
                throw BusinessException.unprocessable("INITIAL_MAINTENANCE_INVALID_DATE",
                        "A manutenção histórica deve ocorrer entre a compra e a data de corte.");
            }
        } else {
            if (performedAt.isBefore(device.getPurchasedAt())) {
                throw BusinessException.unprocessable("MAINTENANCE_DATE_BEFORE_PURCHASE",
                        "A manutenção não pode ser anterior à compra do aparelho.");
            }
            if (initialization.isPresent()
                    && !performedAt.isAfter(initialization.get().getCutoffAt())) {
                throw BusinessException.unprocessable("MAINTENANCE_REQUIRES_OPERATIONAL_PERIOD",
                        "A manutenção operacional deve ocorrer depois da data de corte.");
            }
        }
    }

    private UUID reverseIfNecessary(
            Maintenance maintenance,
            BigDecimal total,
            Instant at,
            String reason
    ) {
        if (maintenance.getRegistrationOrigin() != RegistrationOrigin.OPERATIONAL
                || total.signum() == 0) return null;
        FinancialTransaction original = transactions
                .findActiveMaintenanceTransaction(maintenance.getId())
                .orElseThrow(() -> BusinessException.conflict("MAINTENANCE_LEDGER_MISSING",
                        "O lançamento financeiro ativo da manutenção não foi encontrado."));
        return transactions.saveAndFlush(FinancialTransaction.maintenanceReversal(
                original, at, "Cancelamento de manutenção: " + reason)).getId();
    }

    private Maintenance detail(UUID deviceId, UUID maintenanceId) {
        return maintenances.findDetail(deviceId, maintenanceId)
                .orElseThrow(MaintenanceService::maintenanceNotFound);
    }

    private Device lockedDevice(UUID id) {
        return devices.findByIdForUpdate(id).orElseThrow(MaintenanceService::deviceNotFound);
    }

    private Device requireDevice(UUID id) {
        return devices.findById(id).orElseThrow(MaintenanceService::deviceNotFound);
    }

    private static void ensureMaintainable(Device device) {
        if (device.getArchivedAt() != null) {
            throw BusinessException.unprocessable("DEVICE_ARCHIVED", "O aparelho está arquivado.");
        }
        ensureNoActiveSale(device);
    }

    private static void ensureNoActiveSale(Device device) {
        if (device.getStatus() == DeviceStatus.VENDIDO) {
            throw BusinessException.unprocessable("DEVICE_ALREADY_SOLD",
                    "O aparelho possui uma venda ativa.");
        }
    }

    private Map<UUID, BigDecimal> totals(Collection<Maintenance> values) {
        if (values.isEmpty()) return Map.of();
        Map<UUID, BigDecimal> totals = new HashMap<>();
        maintenances.findTotals(values.stream().map(Maintenance::getId).toList())
                .forEach(value -> totals.put(value.getMaintenanceId(), value.getTotal()));
        return totals;
    }

    private static Map<String, Object> auditChanges(Maintenance maintenance, BigDecimal total) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("deviceId", maintenance.getDevice().getId().toString());
        values.put("registrationOrigin", maintenance.getRegistrationOrigin().name());
        values.put("itemCount", maintenance.getItems().size());
        values.put("total", total);
        return values;
    }

    private static Specification<Maintenance> filter(
            UUID deviceId,
            MaintenanceStatus status,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("device").get("id"), deviceId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("performedAt"), to));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static BusinessException deviceNotFound() {
        return BusinessException.notFound("DEVICE_NOT_FOUND", "Aparelho não encontrado.");
    }

    private static BusinessException maintenanceNotFound() {
        return BusinessException.notFound("MAINTENANCE_NOT_FOUND", "Manutenção não encontrada.");
    }

    public record ArchiveMaintenanceResult(int cancelledCount, BigDecimal reversedAmount) {
    }
}
