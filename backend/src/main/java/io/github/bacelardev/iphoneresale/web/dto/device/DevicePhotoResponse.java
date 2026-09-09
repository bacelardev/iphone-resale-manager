package io.github.bacelardev.iphoneresale.web.dto.device;

import io.github.bacelardev.iphoneresale.domain.model.DevicePhoto;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.time.Instant;
import java.util.UUID;

public record DevicePhotoResponse(
        UUID id,
        String url,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        int position,
        Instant createdAt,
        UserReference createdBy
) {
    public static DevicePhotoResponse from(DevicePhoto photo, String url) {
        return new DevicePhotoResponse(
                photo.getId(), url, photo.getOriginalFilename(), photo.getMimeType(),
                photo.getSizeBytes(), photo.getPosition(), photo.getCreatedAt(),
                UserReference.from(photo.getCreatedBy())
        );
    }
}
