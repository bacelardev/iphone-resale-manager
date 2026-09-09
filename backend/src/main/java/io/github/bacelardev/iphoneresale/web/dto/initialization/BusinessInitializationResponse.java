package io.github.bacelardev.iphoneresale.web.dto.initialization;

import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;

import java.time.Instant;
import java.util.UUID;

public record BusinessInitializationResponse(
        UUID id,
        String status,
        Instant cutoffAt,
        Instant trackingStartedAt,
        Long version
) {

    public static BusinessInitializationResponse notStarted() {
        return new BusinessInitializationResponse(null, "NOT_STARTED", null, null, null);
    }

    public static BusinessInitializationResponse from(BusinessInitialization initialization) {
        Instant trackingStartedAt = initialization.getCompletedAt() == null
                ? null : initialization.getCutoffAt();
        return new BusinessInitializationResponse(
                initialization.getId(),
                initialization.getStatus().name(),
                initialization.getCutoffAt(),
                trackingStartedAt,
                initialization.getVersion()
        );
    }
}
