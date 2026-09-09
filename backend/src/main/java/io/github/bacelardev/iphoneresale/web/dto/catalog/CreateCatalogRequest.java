package io.github.bacelardev.iphoneresale.web.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCatalogRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 80) String name
) {
}
