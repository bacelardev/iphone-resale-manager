package io.github.bacelardev.iphoneresale.web.dto.maintenance;

import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.Maintenance;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceSummaryResponse(
        UUID id,
        UUID deviceId,
        String internalCode,
        Instant performedAt,
        UserReference responsibleUser,
        MaintenanceStatus status,
        RegistrationOrigin registrationOrigin,
        BigDecimal total,
        Instant cancelledAt,
        long version
) {
    public static MaintenanceSummaryResponse from(Maintenance maintenance, BigDecimal total) {
        return new MaintenanceSummaryResponse(
                maintenance.getId(), maintenance.getDevice().getId(),
                maintenance.getDevice().getInternalCode(), maintenance.getPerformedAt(),
                UserReference.from(maintenance.getResponsibleUser()), maintenance.getStatus(),
                maintenance.getRegistrationOrigin(), total, maintenance.getCancelledAt(),
                maintenance.getVersion() == null ? 0 : maintenance.getVersion()
        );
    }
}
