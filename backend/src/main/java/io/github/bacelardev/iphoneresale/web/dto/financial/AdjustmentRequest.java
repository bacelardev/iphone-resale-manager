package io.github.bacelardev.iphoneresale.web.dto.financial;

import io.github.bacelardev.iphoneresale.domain.enums.FinancialDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdjustmentRequest(
        FinancialDirection direction,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        UUID reversalOfTransactionId,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 500) String description
) {
}
