package io.github.bacelardev.iphoneresale.web.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ArchiveDeviceRequest(
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {
}
