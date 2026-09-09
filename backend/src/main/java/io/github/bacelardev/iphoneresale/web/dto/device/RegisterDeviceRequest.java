package io.github.bacelardev.iphoneresale.web.dto.device;

import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RegisterDeviceRequest(
        @NotNull UUID modelId,
        @NotNull UUID colorId,
        @Min(1) int storageGb,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal purchasePrice,
        @NotNull Instant purchasedAt,
        boolean faceIdWorking,
        boolean originalScreen,
        boolean originalBattery,
        @Min(0) @Max(100) int batteryHealthPercent,
        @NotNull DeviceStatus initialStatus
) {
}
