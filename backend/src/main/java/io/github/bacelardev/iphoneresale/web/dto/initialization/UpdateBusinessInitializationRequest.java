package io.github.bacelardev.iphoneresale.web.dto.initialization;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.time.Instant;

public record UpdateBusinessInitializationRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotNull Instant cutoffAt
) {
}
