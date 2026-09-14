package io.github.bacelardev.iphoneresale.web.dto.initialization;

import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import io.github.bacelardev.iphoneresale.domain.model.OwnerCapitalOpening;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BusinessInitializationResponse(
        UUID id,
        String status,
        Instant cutoffAt,
        Instant trackingStartedAt,
        BigDecimal declaredCashBalance,
        UUID openingBalanceTransactionId,
        Instant completedAt,
        UserReference completedBy,
        List<OwnerCapitalOpeningResponse> ownerCapitalOpenings,
        Long version
) {
    public static BusinessInitializationResponse notStarted() {
        return new BusinessInitializationResponse(
                null, "NOT_STARTED", null, null, null, null, null, null, List.of(), null
        );
    }

    public static BusinessInitializationResponse from(BusinessInitialization initialization) {
        return from(initialization, List.of());
    }

    public static BusinessInitializationResponse from(
            BusinessInitialization initialization,
            List<OwnerCapitalOpening> openings
    ) {
        Instant trackingStartedAt = initialization.getCompletedAt() == null
                ? null : initialization.getCutoffAt();
        return new BusinessInitializationResponse(
                initialization.getId(),
                initialization.getStatus().name(),
                initialization.getCutoffAt(),
                trackingStartedAt,
                initialization.getDeclaredCashBalance(),
                initialization.getOpeningBalanceTransaction() == null
                        ? null : initialization.getOpeningBalanceTransaction().getId(),
                initialization.getCompletedAt(),
                UserReference.from(initialization.getCompletedBy()),
                openings.stream().map(OwnerCapitalOpeningResponse::from).toList(),
                initialization.getVersion()
        );
    }
}
