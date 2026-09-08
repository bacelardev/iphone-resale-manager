package io.github.bacelardev.iphoneresale.web.dto.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateModelRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) Integer displayOrder
) {
}
