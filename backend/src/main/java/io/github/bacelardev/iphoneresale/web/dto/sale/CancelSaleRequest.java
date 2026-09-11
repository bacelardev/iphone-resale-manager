package io.github.bacelardev.iphoneresale.web.dto.sale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CancelSaleRequest(
        @NotNull @PositiveOrZero Long saleVersion,
        @NotNull @PositiveOrZero Long deviceVersion,
        @NotBlank @Size(max = 500) String reason
) {
}
