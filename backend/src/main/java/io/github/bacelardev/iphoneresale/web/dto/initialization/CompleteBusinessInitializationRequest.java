package io.github.bacelardev.iphoneresale.web.dto.initialization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CompleteBusinessInitializationRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
        BigDecimal declaredCashBalance,
        @Valid List<OwnerCapitalOpeningRequest> ownerCapitalOpenings
) {
    public CompleteBusinessInitializationRequest {
        ownerCapitalOpenings = ownerCapitalOpenings == null
                ? List.of() : List.copyOf(ownerCapitalOpenings);
    }
}
