package io.github.bacelardev.iphoneresale.application.service.financial;

import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.PageableFactory;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.enums.BusinessInitializationStatus;
import io.github.bacelardev.iphoneresale.domain.enums.FinancialDirection;
import io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AppUserJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.BusinessInitializationJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.FinancialTransactionJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.financial.AdjustmentRequest;
import io.github.bacelardev.iphoneresale.web.dto.financial.FinancialPeriodResponse;
import io.github.bacelardev.iphoneresale.web.dto.financial.FinancialSummaryResponse;
import io.github.bacelardev.iphoneresale.web.dto.financial.FinancialTransactionResponse;
import io.github.bacelardev.iphoneresale.web.dto.financial.OpeningBalanceRequest;
import io.github.bacelardev.iphoneresale.web.dto.financial.OwnerMovementRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FinancialService {

    private static final long INITIALIZATION_ADVISORY_LOCK_KEY = 3_092_026_090_801L;
    private static final Set<FinancialTransactionType> MANUALLY_REVERSIBLE = Set.of(
            FinancialTransactionType.OPENING_BALANCE,
            FinancialTransactionType.OWNER_CONTRIBUTION,
            FinancialTransactionType.OWNER_WITHDRAWAL,
            FinancialTransactionType.MANUAL_ADJUSTMENT
    );
    private static final Map<String, String> SORT = Map.of(
            "occurredAt", "occurredAt",
            "createdAt", "createdAt",
            "amount", "amount",
            "type", "type",
            "direction", "direction"
    );

    private final FinancialTransactionJpaRepository transactions;
    private final BusinessInitializationJpaRepository initializations;
    private final AppUserJpaRepository users;
    private final AuditService audit;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ZoneId businessZone;

    public FinancialService(
            FinancialTransactionJpaRepository transactions,
            BusinessInitializationJpaRepository initializations,
            AppUserJpaRepository users,
            AuditService audit,
            EntityManager entityManager,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.business-time-zone}") String businessTimeZone
    ) {
        this.transactions = transactions;
        this.initializations = initializations;
        this.users = users;
        this.audit = audit;
        this.entityManager = entityManager;
        this.clock = clock;
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    @Transactional
    public FinancialTransactionResponse openingBalance(OpeningBalanceRequest request) {
        advisoryInitializationLock();
        BusinessInitialization initialization = initializations.findSingletonForUpdate()
                .orElseThrow(() -> BusinessException.conflict(
                        "BUSINESS_INITIALIZATION_NOT_STARTED",
                        "Inicie a preparação antes de registrar o saldo inicial."));
        if (initialization.getStatus() != BusinessInitializationStatus.PREPARING) {
            throw BusinessException.conflict(
                    "BUSINESS_ALREADY_INITIALIZED",
                    "A implantação inicial já foi concluída.");
        }
        if (!sameDatabaseInstant(request.occurredAt(), initialization.getCutoffAt())) {
            throw BusinessException.unprocessable(
                    "OPENING_BALANCE_CUTOFF_MISMATCH",
                    "A data do saldo inicial deve ser igual à data de corte da implantação.");
        }
        if (transactions.findActiveOpeningBalanceForUpdate().isPresent()) {
            throw BusinessException.conflict(
                    "OPENING_BALANCE_ALREADY_EXISTS",
                    "Já existe um saldo inicial não estornado.");
        }

        FinancialTransaction transaction = transactions.saveAndFlush(
                FinancialTransaction.openingBalance(
                        money(request.amount()),
                        request.occurredAt(),
                        request.description()
                )
        );
        audit(transaction, "Saldo inicial registrado.");
        return FinancialTransactionResponse.from(transaction);
    }

    @Transactional
    public FinancialTransactionResponse contribution(OwnerMovementRequest request) {
        BusinessInitialization initialization = completedInitializationForUpdate();
        validateOperationalDate(initialization, request.occurredAt());
        AppUser owner = activeOwner(request.ownerUserId());
        FinancialTransaction transaction = transactions.saveAndFlush(
                FinancialTransaction.ownerContribution(
                        owner, money(request.amount()), request.occurredAt(), request.description()
                )
        );
        audit(transaction, "Aporte de sócio registrado.");
        return FinancialTransactionResponse.from(transaction);
    }

    @Transactional
    public FinancialTransactionResponse withdrawal(OwnerMovementRequest request) {
        BusinessInitialization initialization = completedInitializationForUpdate();
        validateOperationalDate(initialization, request.occurredAt());
        AppUser owner = activeOwner(request.ownerUserId());
        FinancialTransaction transaction = transactions.saveAndFlush(
                FinancialTransaction.ownerWithdrawal(
                        owner, money(request.amount()), request.occurredAt(), request.description()
                )
        );
        audit(transaction, "Retirada de sócio registrada.");
        return FinancialTransactionResponse.from(transaction);
    }

    @Transactional
    public FinancialTransactionResponse adjustment(AdjustmentRequest request) {
        BusinessInitialization initialization = completedInitializationForUpdate();
        validateOperationalDate(initialization, request.occurredAt());

        boolean reversal = request.reversalOfTransactionId() != null;
        boolean regular = request.direction() != null && request.amount() != null;
        if (reversal == regular) {
            throw BusinessException.badRequest("INVALID_FINANCIAL_OPERATION",
                    "Informe direção e valor para um ajuste ou apenas a transação original para um estorno.");
        }

        FinancialTransaction transaction;
        if (reversal) {
            FinancialTransaction original = transactions.findByIdForUpdate(
                    request.reversalOfTransactionId()).orElseThrow(() ->
                    BusinessException.notFound("FINANCIAL_TRANSACTION_NOT_FOUND",
                            "Lançamento financeiro não encontrado."));
            if (original.getReversalOf() != null || !MANUALLY_REVERSIBLE.contains(original.getType())) {
                throw BusinessException.unprocessable("OPERATIONAL_REVERSAL_NOT_ALLOWED",
                        "Este lançamento não pode ser estornado manualmente.");
            }
            if (transactions.existsByReversalOfId(original.getId())) {
                throw BusinessException.conflict("FINANCIAL_TRANSACTION_ALREADY_REVERSED",
                        "Este lançamento já foi estornado.");
            }
            transaction = transactions.saveAndFlush(FinancialTransaction.manualReversal(
                    original, request.occurredAt(), request.description()
            ));
        } else {
            transaction = transactions.saveAndFlush(FinancialTransaction.manualAdjustment(
                    request.direction(), money(request.amount()),
                    request.occurredAt(), request.description()
            ));
        }
        audit(transaction, reversal ? "Lançamento financeiro estornado." : "Ajuste manual registrado.");
        return FinancialTransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> list(
            Instant from,
            Instant to,
            List<FinancialTransactionType> types,
            List<FinancialDirection> directions,
            int page,
            int size,
            String sort
    ) {
        validatePeriodPair(from, to);
        Specification<FinancialTransaction> specification = Specification.where(null);
        if (from != null) {
            specification = specification
                    .and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from))
                    .and((root, query, cb) -> cb.lessThan(root.get("occurredAt"), to));
        }
        if (types != null && !types.isEmpty()) {
            specification = specification.and((root, query, cb) -> root.get("type").in(types));
        }
        if (directions != null && !directions.isEmpty()) {
            specification = specification.and((root, query, cb) -> root.get("direction").in(directions));
        }
        Pageable pageable = PageableFactory.create(
                page, size, sort, SORT, "occurredAt", Sort.Direction.DESC
        );
        return PageResponse.from(transactions.findAll(specification, pageable),
                FinancialTransactionResponse::from);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FinancialSummaryResponse summary(Instant requestedFrom, Instant requestedTo) {
        Instant[] period = period(requestedFrom, requestedTo);
        Instant from = period[0];
        Instant to = period[1];

        BigDecimal opening = scalar("""
                select coalesce(sum(case when direction = 'INFLOW' then amount else -amount end), 0)
                  from financial_transaction where occurred_at < :from
                """, Map.of("from", from));
        BigDecimal movement = scalar("""
                select coalesce(sum(case when direction = 'INFLOW' then amount else -amount end), 0)
                  from financial_transaction
                 where occurred_at >= :from and occurred_at < :to
                """, Map.of("from", from, "to", to));
        BigDecimal revenue = scalar("""
                select coalesce(sum(sale_price), 0) from sale
                 where status = 'ACTIVE' and sold_at >= :from and sold_at < :to
                """, Map.of("from", from, "to", to));
        BigDecimal purchaseCost = scalar("""
                select coalesce(sum(original.amount), 0)
                  from financial_transaction original
                 where original.type = 'DEVICE_PURCHASE'
                   and original.occurred_at >= :from and original.occurred_at < :to
                   and not exists (
                       select 1 from financial_transaction reversal
                        where reversal.reversal_of_id = original.id
                   )
                """, Map.of("from", from, "to", to));
        BigDecimal maintenanceCost = scalar("""
                select coalesce(sum(item.cost), 0)
                  from maintenance maintenance
                  join maintenance_item item on item.maintenance_id = maintenance.id
                 where maintenance.status = 'ACTIVE'
                   and maintenance.registration_origin = 'OPERATIONAL'
                   and maintenance.performed_at >= :from and maintenance.performed_at < :to
                """, Map.of("from", from, "to", to));
        BigDecimal profit = scalar("""
                select coalesce(sum(
                    sale.sale_price - device.purchase_price - coalesce(cost.total, 0)
                ), 0)
                  from sale sale
                  join device device on device.id = sale.device_id
                  left join (
                      select maintenance.device_id, sum(item.cost) total
                        from maintenance maintenance
                        join maintenance_item item on item.maintenance_id = maintenance.id
                       where maintenance.status = 'ACTIVE'
                       group by maintenance.device_id
                  ) cost on cost.device_id = device.id
                 where sale.status = 'ACTIVE'
                   and sale.sold_at >= :from and sale.sold_at < :to
                """, Map.of("from", from, "to", to));
        BigDecimal stockCapital = scalar("""
                select coalesce(sum(device.purchase_price + coalesce(cost.total, 0)), 0)
                  from device device
                  left join (
                      select maintenance.device_id, sum(item.cost) total
                        from maintenance maintenance
                        join maintenance_item item on item.maintenance_id = maintenance.id
                       where maintenance.status = 'ACTIVE'
                       group by maintenance.device_id
                  ) cost on cost.device_id = device.id
                 where device.archived_at is null and device.status <> 'VENDIDO'
                """, Map.of());
        BigDecimal margin = revenue.signum() == 0 ? null
                : profit.multiply(BigDecimal.valueOf(100))
                        .divide(revenue, 4, RoundingMode.HALF_UP);

        return new FinancialSummaryResponse(
                new FinancialPeriodResponse(from, to, businessZone.getId()),
                opening, opening.add(movement).setScale(2),
                revenue, purchaseCost, maintenanceCost, profit,
                margin, stockCapital, clock.instant()
        );
    }

    private BusinessInitialization completedInitializationForUpdate() {
        BusinessInitialization initialization = initializations.findSingletonForUpdate()
                .orElseThrow(() -> BusinessException.conflict(
                        "BUSINESS_INITIALIZATION_NOT_STARTED",
                        "A implantação inicial ainda não foi iniciada."));
        if (initialization.getStatus() != BusinessInitializationStatus.COMPLETED) {
            throw BusinessException.unprocessable(
                    "FINANCIAL_OPERATION_REQUIRES_OPERATIONAL_PERIOD",
                    "Conclua a implantação inicial antes de registrar movimentos financeiros.");
        }
        return initialization;
    }

    private AppUser activeOwner(UUID id) {
        return users.findByIdAndActiveTrue(id).orElseThrow(() ->
                BusinessException.badRequest("INVALID_FINANCIAL_OPERATION",
                        "O sócio relacionado não existe ou está inativo."));
    }

    private static boolean sameDatabaseInstant(Instant first, Instant second) {
        return Duration.between(first, second).abs()
                .compareTo(Duration.ofNanos(1_000)) <= 0;
    }

    private static void validateOperationalDate(
            BusinessInitialization initialization,
            Instant occurredAt
    ) {
        if (!occurredAt.isAfter(initialization.getCutoffAt())) {
            throw BusinessException.unprocessable(
                    "FINANCIAL_OPERATION_REQUIRES_OPERATIONAL_PERIOD",
                    "A data econômica deve ser posterior à data de corte.");
        }
    }

    private void audit(FinancialTransaction transaction, String summary) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("type", transaction.getType().name());
        changes.put("direction", transaction.getDirection().name());
        changes.put("amount", transaction.getAmount());
        changes.put("occurredAt", transaction.getOccurredAt().toString());
        if (transaction.getOwnerUser() != null) {
            changes.put("ownerUserId", transaction.getOwnerUser().getId().toString());
        }
        if (transaction.getReversalOf() != null) {
            changes.put("reversalOfId", transaction.getReversalOf().getId().toString());
        }
        audit.record(AuditAction.FINANCIAL_TRANSACTION_CREATED,
                AuditedEntityType.FINANCIAL_TRANSACTION,
                transaction.getId(), transaction.getType().name(), summary, changes);
    }

    private Instant[] period(Instant from, Instant to) {
        if (from == null && to == null) {
            ZonedDateTime now = clock.instant().atZone(businessZone);
            ZonedDateTime start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(businessZone);
            return new Instant[]{start.toInstant(), start.plusMonths(1).toInstant()};
        }
        validatePeriodPair(from, to);
        return new Instant[]{from, to};
    }

    private static void validatePeriodPair(Instant from, Instant to) {
        if ((from == null) != (to == null) || (from != null && !from.isBefore(to))) {
            throw BusinessException.badRequest("VALIDATION_ERROR",
                    "Informe um período válido com início anterior ao fim.");
        }
    }

    private void advisoryInitializationLock() {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", INITIALIZATION_ADVISORY_LOCK_KEY)
                .getSingleResult();
    }

    private BigDecimal scalar(String sql, Map<String, Object> parameters) {
        Query query = entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        Object value = query.getSingleResult();
        BigDecimal decimal = value instanceof BigDecimal bigDecimal
                ? bigDecimal : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
