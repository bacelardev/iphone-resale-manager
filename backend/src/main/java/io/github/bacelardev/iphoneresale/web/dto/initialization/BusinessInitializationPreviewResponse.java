package io.github.bacelardev.iphoneresale.web.dto.initialization;

import java.math.BigDecimal;
import java.time.Instant;

public record BusinessInitializationPreviewResponse(
        String status,
        Instant cutoffAt,
        long initialDeviceCount,
        BigDecimal purchaseCapital,
        BigDecimal maintenanceCapital,
        BigDecimal stockCapital
) {
}
