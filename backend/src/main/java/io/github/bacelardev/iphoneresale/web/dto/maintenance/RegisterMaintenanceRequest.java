package io.github.bacelardev.iphoneresale.web.dto.maintenance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record RegisterMaintenanceRequest(
        @NotNull Instant performedAt,
        @NotEmpty List<@Valid MaintenanceItemRequest> items
) {
}
