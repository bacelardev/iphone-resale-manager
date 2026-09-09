package io.github.bacelardev.iphoneresale.web.dto.common;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VersionRequest(@NotNull @Min(0) Long expectedVersion) {
}
