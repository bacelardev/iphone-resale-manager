package io.github.bacelardev.iphoneresale.web.dto.maintenance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record MaintenanceItemRequest(
        @NotNull UUID partId,
        String details,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal cost
) {
}
