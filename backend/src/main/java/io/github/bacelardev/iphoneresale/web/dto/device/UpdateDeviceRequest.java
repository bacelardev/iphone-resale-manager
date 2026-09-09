package io.github.bacelardev.iphoneresale.web.dto.device;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UpdateDeviceRequest(
        @NotNull @Min(0) Long expectedVersion,
        UUID modelId,
        UUID colorId,
        @Min(1) Integer storageGb,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal purchasePrice,
        Instant purchasedAt,
        Boolean faceIdWorking,
        Boolean originalScreen,
        Boolean originalBattery,
        @Min(0) @Max(100) Integer batteryHealthPercent
) {
    @AssertTrue(message = "Informe ao menos um campo para atualização.")
    public boolean isAnyFieldPresent() {
        return modelId != null || colorId != null || storageGb != null || purchasePrice != null
                || purchasedAt != null || faceIdWorking != null || originalScreen != null
                || originalBattery != null || batteryHealthPercent != null;
    }
}
