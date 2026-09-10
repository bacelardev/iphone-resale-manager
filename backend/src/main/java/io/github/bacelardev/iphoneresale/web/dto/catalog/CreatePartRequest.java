package io.github.bacelardev.iphoneresale.web.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePartRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name
) {
}
