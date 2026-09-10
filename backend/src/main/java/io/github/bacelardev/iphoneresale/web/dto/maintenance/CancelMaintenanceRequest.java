package io.github.bacelardev.iphoneresale.web.dto.maintenance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelMaintenanceRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {
}
