package io.github.bacelardev.iphoneresale.web.dto.initialization;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record StartBusinessInitializationRequest(
        @NotNull Instant cutoffAt
) {
}
