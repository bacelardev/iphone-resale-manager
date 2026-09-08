package io.github.bacelardev.iphoneresale.web.dto.initialization;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record UpdateBusinessInitializationRequest(
        @PositiveOrZero long expectedVersion,
        @NotNull Instant cutoffAt
) {
}
