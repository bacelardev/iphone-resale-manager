package io.github.bacelardev.iphoneresale.web.dto.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCatalogRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 80) String name
) {
}
