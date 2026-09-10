package io.github.bacelardev.iphoneresale.web.dto.maintenance;

import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.Maintenance;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaintenanceDetailResponse(
        UUID id,
        UUID deviceId,
        String internalCode,
        Instant performedAt,
        UserReference responsibleUser,
        MaintenanceStatus status,
        RegistrationOrigin registrationOrigin,
        List<MaintenanceItemResponse> items,
        BigDecimal total,
        String financialImpact,
        Instant createdAt,
        Instant cancelledAt,
        UserReference cancelledBy,
        String cancellationReason,
        long version
) {
    public static MaintenanceDetailResponse from(Maintenance maintenance) {
        BigDecimal total = maintenance.total();
        String impact = maintenance.getRegistrationOrigin() == RegistrationOrigin.INITIAL_IMPORT
                ? "HISTORICAL_COST_ONLY"
                : total.signum() > 0 ? "OUTFLOW_CREATED" : "NO_FINANCIAL_COST";
        return new MaintenanceDetailResponse(
                maintenance.getId(), maintenance.getDevice().getId(),
                maintenance.getDevice().getInternalCode(), maintenance.getPerformedAt(),
                UserReference.from(maintenance.getResponsibleUser()), maintenance.getStatus(),
                maintenance.getRegistrationOrigin(),
                maintenance.getItems().stream().map(MaintenanceItemResponse::from).toList(),
                total, impact, maintenance.getCreatedAt(), maintenance.getCancelledAt(),
                UserReference.from(maintenance.getCancelledBy()), maintenance.getCancellationReason(),
                maintenance.getVersion() == null ? 0 : maintenance.getVersion()
        );
    }
}
