package io.github.bacelardev.iphoneresale.web.dto.catalog;

import io.github.bacelardev.iphoneresale.domain.model.PartCatalog;

import java.time.Instant;
import java.util.UUID;

public record PartCatalogResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static PartCatalogResponse from(PartCatalog part) {
        return new PartCatalogResponse(
                part.getId(), part.getCode(), part.getName(), part.isActive(),
                part.getCreatedAt(), part.getUpdatedAt(),
                part.getVersion() == null ? 0 : part.getVersion()
        );
    }
}
