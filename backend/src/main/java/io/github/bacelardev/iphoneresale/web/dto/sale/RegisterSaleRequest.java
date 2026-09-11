package io.github.bacelardev.iphoneresale.web.dto.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record RegisterSaleRequest(
        @NotNull @PositiveOrZero Long deviceVersion,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 12, fraction = 2)
        BigDecimal salePrice,
        @NotNull Instant soldAt
) {
}
