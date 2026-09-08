package io.github.bacelardev.iphoneresale.web.dto.device;

import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.web.dto.common.CatalogReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceSummaryResponse(
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
        String coverPhotoUrl,
        Instant updatedAt,
        long version
) {
}
