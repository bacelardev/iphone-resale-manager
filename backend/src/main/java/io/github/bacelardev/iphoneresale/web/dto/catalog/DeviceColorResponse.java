package io.github.bacelardev.iphoneresale.web.dto.catalog;

import io.github.bacelardev.iphoneresale.domain.model.DeviceColor;

import java.time.Instant;
import java.util.UUID;

public record DeviceColorResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static DeviceColorResponse from(DeviceColor color) {
        return new DeviceColorResponse(
                color.getId(), color.getCode(), color.getName(), color.isActive(),
                color.getCreatedAt(), color.getUpdatedAt(), color.getVersion()
        );
    }
}
