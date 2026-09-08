package io.github.bacelardev.iphoneresale.web.dto.catalog;

import io.github.bacelardev.iphoneresale.domain.model.IphoneModel;

import java.time.Instant;
import java.util.UUID;

public record IphoneModelResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static IphoneModelResponse from(IphoneModel model) {
        return new IphoneModelResponse(
                model.getId(), model.getCode(), model.getName(), model.isActive(),
                model.getDisplayOrder(), model.getCreatedAt(), model.getUpdatedAt(),
                model.getVersion()
        );
    }
}
