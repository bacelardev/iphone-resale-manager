package io.github.bacelardev.iphoneresale.web.dto.device;

import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.web.dto.common.CatalogReference;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeviceDetailResponse(
        UUID id,
        String internalCode,
        CatalogReference model,
        CatalogReference color,
        int storageGb,
        BigDecimal purchasePrice,
        BigDecimal maintenanceTotal,
        BigDecimal investmentTotal,
        Instant purchasedAt,
        DeviceStatus status,
        RegistrationOrigin registrationOrigin,
        boolean archived,
        boolean faceIdWorking,
        boolean originalScreen,
        boolean originalBattery,
        Integer batteryHealthPercent,
        List<DevicePhotoResponse> photos,
        Instant createdAt,
        UserReference createdBy,
        Instant updatedAt,
        UserReference updatedBy,
        Instant archivedAt,
        UserReference archivedBy,
        long version
) {
}
