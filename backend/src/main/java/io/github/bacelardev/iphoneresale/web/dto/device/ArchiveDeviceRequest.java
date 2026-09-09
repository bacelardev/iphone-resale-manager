package io.github.bacelardev.iphoneresale.web.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ArchiveDeviceRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {
}
