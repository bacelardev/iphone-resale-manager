package io.github.bacelardev.iphoneresale.application.service.initialization;

import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.enums.BusinessInitializationStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.BusinessInitializationJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DeviceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.MaintenanceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.SaleJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.initialization.BusinessInitializationPreviewResponse;
import io.github.bacelardev.iphoneresale.web.dto.initialization.BusinessInitializationResponse;
import io.github.bacelardev.iphoneresale.web.dto.initialization.StartBusinessInitializationRequest;
import io.github.bacelardev.iphoneresale.web.dto.initialization.UpdateBusinessInitializationRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class BusinessInitializationService {

    private static final long ADVISORY_LOCK_KEY = 3_092_026_090_801L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final BusinessInitializationJpaRepository initializations;
    private final DeviceJpaRepository devices;
    private final MaintenanceJpaRepository maintenances;
    private final SaleJpaRepository sales;
    private final AuditService audit;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ZoneId businessZone;

    public BusinessInitializationService(
            BusinessInitializationJpaRepository initializations,
            DeviceJpaRepository devices,
            MaintenanceJpaRepository maintenances,
            SaleJpaRepository sales,
            AuditService audit,
            EntityManager entityManager,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.business-time-zone}") String businessTimeZone
    ) {
        this.initializations = initializations;
        this.devices = devices;
        this.maintenances = maintenances;
        this.sales = sales;
        this.audit = audit;
        this.entityManager = entityManager;
        this.clock = clock;
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    @Transactional
    public BusinessInitializationResponse start(StartBusinessInitializationRequest request) {
        validateCutoff(request.cutoffAt());
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", ADVISORY_LOCK_KEY)
                .getSingleResult();
        initializations.findSingleton().ifPresent(existing -> {
            if (existing.getStatus() == BusinessInitializationStatus.COMPLETED) {
                throw BusinessException.conflict("BUSINESS_ALREADY_INITIALIZED",
                        "A implantação inicial já foi concluída.");
            }
            throw BusinessException.conflict("BUSINESS_INITIALIZATION_ALREADY_STARTED",
                    "A preparação da implantação inicial já foi iniciada.");
        });
        BusinessInitialization initialization = initializations.saveAndFlush(
                new BusinessInitialization(request.cutoffAt()));
        audit.record(AuditAction.CREATED, AuditedEntityType.BUSINESS_INITIALIZATION,
                initialization.getId(), "PREPARING",
                "Configuração inicial iniciada com data de corte em "
                        + DATE.format(request.cutoffAt().atZone(businessZone)) + ".",
                Map.of("cutoffAt", request.cutoffAt().toString()));
        return BusinessInitializationResponse.from(initialization);
    }

    @Transactional(readOnly = true)
    public BusinessInitializationResponse get() {
        return initializations.findSingleton()
                .map(BusinessInitializationResponse::from)
                .orElseGet(BusinessInitializationResponse::notStarted);
    }

    @Transactional
    public BusinessInitializationResponse update(UpdateBusinessInitializationRequest request) {
        validateCutoff(request.cutoffAt());
        BusinessInitialization initialization = requiredForUpdate();
        VersionGuard.require(initialization.getVersion(), request.expectedVersion());
        if (initialization.getStatus() != BusinessInitializationStatus.PREPARING) {
            throw BusinessException.conflict("BUSINESS_ALREADY_INITIALIZED",
                    "A implantação inicial já foi concluída.");
        }
        if (devices.existsByRegistrationOrigin(RegistrationOrigin.INITIAL_IMPORT)) {
            throw BusinessException.conflict("INITIALIZATION_CUTOFF_LOCKED",
                    "A data de corte não pode mudar depois da primeira importação inicial.");
        }
        if (maintenances.existsByRegistrationOriginAndPerformedAtLessThanEqual(
                RegistrationOrigin.OPERATIONAL, request.cutoffAt())) {
            throw BusinessException.conflict("INITIALIZATION_CUTOFF_LOCKED",
                    "A data de corte conflita com uma manutenção operacional já registrada.");
        }
        if (sales.existsBySoldAtLessThanEqual(request.cutoffAt())) {
            throw BusinessException.conflict("INITIALIZATION_CUTOFF_LOCKED",
                    "A data de corte conflita com uma venda já registrada.");
        }
        initialization.updateCutoff(request.cutoffAt());
        initializations.flush();
        audit.record(AuditAction.UPDATED, AuditedEntityType.BUSINESS_INITIALIZATION,
                initialization.getId(), "PREPARING", "Data de corte da configuração inicial atualizada.",
                Map.of("cutoffAt", request.cutoffAt().toString()));
        return BusinessInitializationResponse.from(initialization);
    }

    @Transactional(readOnly = true)
    public BusinessInitializationPreviewResponse preview() {
        BusinessInitialization initialization = initializations.findSingleton().orElseThrow(() ->
                BusinessException.conflict("BUSINESS_INITIALIZATION_NOT_STARTED",
                        "Inicie a preparação antes de consultar a prévia."));
        long count = devices.countByRegistrationOriginAndArchivedAtIsNull(RegistrationOrigin.INITIAL_IMPORT);
        BigDecimal purchaseCapital = devices.sumPurchasePriceByOrigin(RegistrationOrigin.INITIAL_IMPORT);
        BigDecimal maintenanceCapital = maintenances.sumActiveInitialImportCost();
        if (maintenanceCapital == null) maintenanceCapital = BigDecimal.ZERO.setScale(2);
        return new BusinessInitializationPreviewResponse(
                initialization.getStatus().name(), initialization.getCutoffAt(), count,
                purchaseCapital, maintenanceCapital, purchaseCapital.add(maintenanceCapital)
        );
    }

    public BusinessInitialization requiredForUpdate() {
        return initializations.findSingletonForUpdate().orElseThrow(() ->
                BusinessException.conflict("BUSINESS_INITIALIZATION_NOT_STARTED",
                        "Inicie a preparação antes de importar o estoque existente."));
    }

    private void validateCutoff(java.time.Instant cutoffAt) {
        if (cutoffAt.isAfter(clock.instant())) {
            throw BusinessException.badRequest("INITIALIZATION_CUTOFF_IN_FUTURE",
                    "A data de corte não pode estar no futuro.");
        }
    }
}
