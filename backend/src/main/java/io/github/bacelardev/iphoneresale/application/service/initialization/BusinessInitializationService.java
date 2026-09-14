package io.github.bacelardev.iphoneresale.application.service.initialization;

import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.enums.BusinessInitializationStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
import io.github.bacelardev.iphoneresale.domain.model.OwnerCapitalOpening;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AppUserJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.BusinessInitializationJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DeviceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.FinancialTransactionJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.MaintenanceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.OwnerCapitalOpeningJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.SaleJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.initialization.BusinessInitializationPreviewResponse;
import io.github.bacelardev.iphoneresale.web.dto.initialization.BusinessInitializationResponse;
import io.github.bacelardev.iphoneresale.web.dto.initialization.CompleteBusinessInitializationRequest;
import io.github.bacelardev.iphoneresale.web.dto.initialization.OwnerCapitalOpeningRequest;
import io.github.bacelardev.iphoneresale.web.dto.initialization.StartBusinessInitializationRequest;
import io.github.bacelardev.iphoneresale.web.dto.initialization.UpdateBusinessInitializationRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BusinessInitializationService {

    private static final long ADVISORY_LOCK_KEY = 3_092_026_090_801L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final BusinessInitializationJpaRepository initializations;
    private final DeviceJpaRepository devices;
    private final MaintenanceJpaRepository maintenances;
    private final SaleJpaRepository sales;
    private final FinancialTransactionJpaRepository transactions;
    private final OwnerCapitalOpeningJpaRepository ownerCapital;
    private final AppUserJpaRepository users;
    private final AuditService audit;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ZoneId businessZone;

    public BusinessInitializationService(
            BusinessInitializationJpaRepository initializations,
            DeviceJpaRepository devices,
            MaintenanceJpaRepository maintenances,
            SaleJpaRepository sales,
            FinancialTransactionJpaRepository transactions,
            OwnerCapitalOpeningJpaRepository ownerCapital,
            AppUserJpaRepository users,
            AuditService audit,
            EntityManager entityManager,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.business-time-zone}") String businessTimeZone
    ) {
        this.initializations = initializations;
        this.devices = devices;
        this.maintenances = maintenances;
        this.sales = sales;
        this.transactions = transactions;
        this.ownerCapital = ownerCapital;
        this.users = users;
        this.audit = audit;
        this.entityManager = entityManager;
        this.clock = clock;
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    @Transactional
    public BusinessInitializationResponse start(StartBusinessInitializationRequest request) {
        validateCutoff(request.cutoffAt());
        advisoryLock();
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
        return response(initialization);
    }

    @Transactional(readOnly = true)
    public BusinessInitializationResponse get() {
        return initializations.findSingleton()
                .map(this::response)
                .orElseGet(BusinessInitializationResponse::notStarted);
    }

    @Transactional
    public BusinessInitializationResponse update(UpdateBusinessInitializationRequest request) {
        validateCutoff(request.cutoffAt());
        BusinessInitialization initialization = requiredForUpdate();
        VersionGuard.require(initialization.getVersion(), request.expectedVersion());
        requirePreparing(initialization);
        if (devices.existsByRegistrationOrigin(RegistrationOrigin.INITIAL_IMPORT)) {
            throw BusinessException.conflict("INITIALIZATION_CUTOFF_LOCKED",
                    "A data de corte não pode mudar depois da primeira importação inicial.");
        }
        if (maintenances.existsByRegistrationOriginAndPerformedAtLessThanEqual(
                RegistrationOrigin.OPERATIONAL, request.cutoffAt())
                || sales.existsBySoldAtLessThanEqual(request.cutoffAt())) {
            throw BusinessException.conflict("INITIALIZATION_CUTOFF_LOCKED",
                    "A data de corte conflita com uma operação já registrada.");
        }
        initialization.updateCutoff(request.cutoffAt());
        initializations.flush();
        audit.record(AuditAction.UPDATED, AuditedEntityType.BUSINESS_INITIALIZATION,
                initialization.getId(), "PREPARING", "Data de corte da configuração inicial atualizada.",
                Map.of("cutoffAt", request.cutoffAt().toString()));
        return response(initialization);
    }

    @Transactional(readOnly = true)
    public BusinessInitializationPreviewResponse preview() {
        BusinessInitialization initialization = initializations.findSingleton().orElseThrow(() ->
                BusinessException.conflict("BUSINESS_INITIALIZATION_NOT_STARTED",
                        "Inicie a preparação antes de consultar a prévia."));
        long count = devices.countByRegistrationOriginAndArchivedAtIsNull(RegistrationOrigin.INITIAL_IMPORT);
        BigDecimal purchaseCapital = money(devices.sumPurchasePriceByOrigin(RegistrationOrigin.INITIAL_IMPORT));
        BigDecimal maintenanceCapital = money(maintenances.sumActiveInitialImportCost());
        return new BusinessInitializationPreviewResponse(
                initialization.getStatus().name(), initialization.getCutoffAt(), count,
                purchaseCapital, maintenanceCapital, purchaseCapital.add(maintenanceCapital)
        );
    }

    @Transactional
    public BusinessInitializationResponse complete(CompleteBusinessInitializationRequest request) {
        advisoryLock();
        BusinessInitialization initialization = requiredForUpdate();
        VersionGuard.require(initialization.getVersion(), request.expectedVersion());
        requirePreparing(initialization);
        validateUniqueOwners(request.ownerCapitalOpenings());

        BigDecimal declaredCash = money(request.declaredCashBalance());
        FinancialTransaction opening = reconcileOpeningBalance(initialization, declaredCash);
        AppUser actor = audit.actor();

        for (OwnerCapitalOpeningRequest item : request.ownerCapitalOpenings()) {
            BigDecimal contribution = money(item.historicalContributionAmount());
            BigDecimal withdrawal = money(item.historicalWithdrawalAmount());
            if (contribution.signum() == 0 && withdrawal.signum() == 0) continue;
            AppUser owner = users.findById(item.ownerUserId()).orElseThrow(() ->
                    BusinessException.badRequest("INVALID_FINANCIAL_OPERATION",
                            "O sócio informado não existe."));
            ownerCapital.save(new OwnerCapitalOpening(
                    initialization, owner, contribution, withdrawal
            ));
        }
        ownerCapital.flush();

        initialization.complete(declaredCash, opening, clock.instant(), actor);
        initializations.flush();

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", Map.of("from", "PREPARING", "to", "COMPLETED"));
        changes.put("cutoffAt", initialization.getCutoffAt().toString());
        changes.put("declaredCashBalance", declaredCash);
        changes.put("openingBalanceTransactionId", opening == null ? null : opening.getId().toString());
        changes.put("ownerCapitalOpeningCount",
                ownerCapital.findByBusinessInitializationIdOrderByOwnerUserNameAsc(
                        initialization.getId()).size());
        audit.record(AuditAction.UPDATED, AuditedEntityType.BUSINESS_INITIALIZATION,
                initialization.getId(), "COMPLETED",
                "Implantação inicial concluída de forma definitiva.", changes);
        return response(initialization);
    }

    public BusinessInitialization requiredForUpdate() {
        return initializations.findSingletonForUpdate().orElseThrow(() ->
                BusinessException.conflict("BUSINESS_INITIALIZATION_NOT_STARTED",
                        "Inicie a preparação antes de continuar."));
    }

    private FinancialTransaction reconcileOpeningBalance(
            BusinessInitialization initialization,
            BigDecimal declaredCash
    ) {
        FinancialTransaction existing = transactions.findActiveOpeningBalanceForUpdate().orElse(null);
        if (declaredCash.signum() == 0) {
            if (existing != null) {
                throw BusinessException.conflict("INITIALIZATION_OPENING_BALANCE_MISMATCH",
                        "Já existe um saldo inicial ativo incompatível com o valor declarado.");
            }
            return null;
        }
        if (existing != null) {
            if (existing.getAmount().compareTo(declaredCash) != 0
                    || !existing.getOccurredAt().equals(initialization.getCutoffAt())) {
                throw BusinessException.conflict("INITIALIZATION_OPENING_BALANCE_MISMATCH",
                        "O saldo inicial existente diverge do caixa declarado ou da data de corte.");
            }
            return existing;
        }
        FinancialTransaction opening = transactions.saveAndFlush(
                FinancialTransaction.openingBalance(declaredCash, initialization.getCutoffAt())
        );
        auditFinancial(opening, "Saldo inicial registrado na conclusão da implantação.");
        return opening;
    }

    private void auditFinancial(FinancialTransaction transaction, String summary) {
        audit.record(AuditAction.FINANCIAL_TRANSACTION_CREATED,
                AuditedEntityType.FINANCIAL_TRANSACTION,
                transaction.getId(), transaction.getType().name(), summary,
                Map.of(
                        "type", transaction.getType().name(),
                        "direction", transaction.getDirection().name(),
                        "amount", transaction.getAmount(),
                        "occurredAt", transaction.getOccurredAt().toString()
                ));
    }

    private BusinessInitializationResponse response(BusinessInitialization initialization) {
        List<OwnerCapitalOpening> openings =
                ownerCapital.findByBusinessInitializationIdOrderByOwnerUserNameAsc(
                        initialization.getId());
        return BusinessInitializationResponse.from(initialization, openings);
    }

    private void advisoryLock() {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", ADVISORY_LOCK_KEY)
                .getSingleResult();
    }

    private static void validateUniqueOwners(List<OwnerCapitalOpeningRequest> openings) {
        Set<UUID> owners = new HashSet<>();
        for (OwnerCapitalOpeningRequest opening : openings) {
            if (!owners.add(opening.ownerUserId())) {
                throw BusinessException.badRequest("OWNER_CAPITAL_DUPLICATE",
                        "Cada sócio pode aparecer uma única vez no capital histórico.");
            }
        }
    }

    private static void requirePreparing(BusinessInitialization initialization) {
        if (initialization.getStatus() != BusinessInitializationStatus.PREPARING) {
            throw BusinessException.conflict("BUSINESS_ALREADY_INITIALIZED",
                    "A implantação inicial já foi concluída.");
        }
    }

    private void validateCutoff(java.time.Instant cutoffAt) {
        if (cutoffAt.isAfter(clock.instant())) {
            throw BusinessException.badRequest("INITIALIZATION_CUTOFF_IN_FUTURE",
                    "A data de corte não pode estar no futuro.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.UNNECESSARY);
    }
}
