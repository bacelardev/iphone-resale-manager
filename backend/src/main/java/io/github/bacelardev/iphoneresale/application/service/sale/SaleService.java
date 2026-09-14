package io.github.bacelardev.iphoneresale.application.service.sale;

import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.SaleStatus;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import io.github.bacelardev.iphoneresale.domain.model.Device;
import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
import io.github.bacelardev.iphoneresale.domain.model.Sale;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.BusinessInitializationJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DeviceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.FinancialTransactionJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.MaintenanceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.SaleJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.sale.CancelSaleRequest;
import io.github.bacelardev.iphoneresale.web.dto.sale.RegisterSaleRequest;
import io.github.bacelardev.iphoneresale.web.dto.sale.SaleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SaleService {

    private final DeviceJpaRepository devices;
    private final SaleJpaRepository sales;
    private final MaintenanceJpaRepository maintenances;
    private final FinancialTransactionJpaRepository transactions;
    private final BusinessInitializationJpaRepository initializations;
    private final AuditService audit;
    private final Clock clock;

    public SaleService(
            DeviceJpaRepository devices,
            SaleJpaRepository sales,
            MaintenanceJpaRepository maintenances,
            FinancialTransactionJpaRepository transactions,
            BusinessInitializationJpaRepository initializations,
            AuditService audit,
            Clock clock
    ) {
        this.devices = devices;
        this.sales = sales;
        this.maintenances = maintenances;
        this.transactions = transactions;
        this.initializations = initializations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SaleResponse register(UUID deviceId, RegisterSaleRequest request) {
        Device device = lockedDevice(deviceId);
        VersionGuard.require(device.getVersion(), request.deviceVersion());
        ensureSaleCanBeRegistered(device);
        validateSoldAt(device, request.soldAt());
        BigDecimal maintenanceTotal = activeMaintenanceTotal(deviceId);
        AppUser actor = audit.actor();
        Sale sale = sales.saveAndFlush(Sale.active(
                device, request.salePrice().setScale(2, RoundingMode.UNNECESSARY),
                request.soldAt(), actor));
        device.changeStatus(DeviceStatus.VENDIDO);
        devices.flush();
        FinancialTransaction transaction = transactions.saveAndFlush(FinancialTransaction.sale(sale));
        SaleResponse response = SaleResponse.from(sale, maintenanceTotal);
        Map<String, Object> changes = financialChanges(response);
        changes.put("deviceStatus", Map.of(
                "from", DeviceStatus.DISPONIVEL_VENDA.name(),
                "to", DeviceStatus.VENDIDO.name()));
        changes.put("transactionId", transaction.getId().toString());
        audit.record(AuditAction.SALE_REGISTERED, AuditedEntityType.SALE, sale.getId(),
                device.getInternalCode(), "Venda registrada para o aparelho "
                        + device.getInternalCode() + ".", changes);
        return response;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SaleResponse getActive(UUID deviceId) {
        if (!devices.existsById(deviceId)) throw deviceNotFound();
        Sale sale = sales.findByDeviceIdAndStatus(deviceId, SaleStatus.ACTIVE)
                .orElseThrow(SaleService::saleNotFound);
        return SaleResponse.from(sale, activeMaintenanceTotal(deviceId));
    }

    @Transactional
    public SaleResponse cancel(UUID deviceId, CancelSaleRequest request) {
        Device device = lockedDevice(deviceId);
        VersionGuard.require(device.getVersion(), request.deviceVersion());
        Sale sale = sales.findByDeviceAndStatusForUpdate(deviceId, SaleStatus.ACTIVE)
                .orElseThrow(() -> sales.findTopByDeviceIdOrderByCreatedAtDesc(deviceId)
                        .map(ignored -> BusinessException.conflict("SALE_ALREADY_CANCELLED",
                                "A venda já foi cancelada."))
                        .orElseGet(SaleService::saleNotFound));
        VersionGuard.require(sale.getVersion(), request.saleVersion());
        if (device.getStatus() != DeviceStatus.VENDIDO) {
            throw BusinessException.unprocessable("INVALID_DEVICE_STATUS_TRANSITION",
                    "O aparelho não está no estado vendido.");
        }
        String reason = request.reason().trim();
        FinancialTransaction original = transactions.findActiveSaleTransaction(sale.getId())
                .orElseThrow(() -> BusinessException.conflict("SALE_LEDGER_MISSING",
                        "O lançamento financeiro ativo da venda não foi encontrado."));
        BigDecimal maintenanceTotal = activeMaintenanceTotal(deviceId);
        SaleResponse active = SaleResponse.from(sale, maintenanceTotal);
        Instant cancelledAt = clock.instant();
        sale.cancel(cancelledAt, audit.actor(), reason);
        sales.flush();
        FinancialTransaction reversal = transactions.saveAndFlush(FinancialTransaction.saleReversal(
                original, cancelledAt, "Cancelamento de venda do aparelho " + device.getInternalCode() + "."));
        device.changeStatus(DeviceStatus.DISPONIVEL_VENDA);
        devices.flush();
        Map<String, Object> changes = financialChanges(active);
        changes.put("reason", reason);
        changes.put("deviceStatus", Map.of(
                "from", DeviceStatus.VENDIDO.name(),
                "to", DeviceStatus.DISPONIVEL_VENDA.name()));
        changes.put("reversalTransactionId", reversal.getId().toString());
        audit.record(AuditAction.SALE_CANCELLED, AuditedEntityType.SALE, sale.getId(),
                device.getInternalCode(), "Venda do aparelho " + device.getInternalCode()
                        + " cancelada.", changes);
        return SaleResponse.from(sale, maintenanceTotal);
    }

    private void ensureSaleCanBeRegistered(Device device) {
        if (device.getArchivedAt() != null) {
            throw BusinessException.unprocessable("DEVICE_ARCHIVED", "O aparelho está arquivado.");
        }
        if (sales.existsByDeviceIdAndStatus(device.getId(), SaleStatus.ACTIVE)) {
            throw BusinessException.conflict("SALE_ALREADY_EXISTS",
                    "O aparelho já possui uma venda ativa.");
        }
        if (device.getStatus() != DeviceStatus.DISPONIVEL_VENDA) {
            throw BusinessException.unprocessable("DEVICE_NOT_AVAILABLE_FOR_SALE",
                    "O aparelho deve estar disponível para venda.");
        }
    }

    private void validateSoldAt(Device device, Instant soldAt) {
        if (soldAt.isBefore(device.getPurchasedAt())) {
            throw BusinessException.unprocessable("SALE_DATE_BEFORE_PURCHASE",
                    "A venda não pode ser anterior à compra do aparelho.");
        }
        Optional<BusinessInitialization> initialization = initializations.findSingletonForUpdate();
        if (initialization.isPresent() && !soldAt.isAfter(initialization.get().getCutoffAt())) {
            throw BusinessException.unprocessable("SALE_REQUIRES_OPERATIONAL_PERIOD",
                    "A venda deve ocorrer depois da data de corte.");
        }
        Instant latestMaintenance = maintenances.latestActivePerformedAtByDeviceId(device.getId());
        if (latestMaintenance != null && soldAt.isBefore(latestMaintenance)) {
            throw BusinessException.unprocessable("SALE_DATE_BEFORE_ACTIVE_MAINTENANCE",
                    "A venda não pode anteceder uma manutenção ativa.");
        }
    }

    private BigDecimal activeMaintenanceTotal(UUID deviceId) {
        BigDecimal value = maintenances.sumActiveCostByDeviceId(deviceId);
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private Device lockedDevice(UUID deviceId) {
        return devices.findByIdForUpdate(deviceId).orElseThrow(SaleService::deviceNotFound);
    }

    private static Map<String, Object> financialChanges(SaleResponse response) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("deviceId", response.deviceId().toString());
        changes.put("salePrice", response.salePrice());
        changes.put("purchasePrice", response.purchasePrice());
        changes.put("maintenanceTotal", response.maintenanceTotal());
        changes.put("investmentTotal", response.investmentTotal());
        changes.put("profit", response.profit());
        changes.put("marginPercent", response.marginPercent());
        return changes;
    }

    private static BusinessException deviceNotFound() {
        return BusinessException.notFound("DEVICE_NOT_FOUND", "Aparelho não encontrado.");
    }

    private static BusinessException saleNotFound() {
        return BusinessException.notFound("SALE_NOT_FOUND", "Venda ativa não encontrada.");
    }
}
