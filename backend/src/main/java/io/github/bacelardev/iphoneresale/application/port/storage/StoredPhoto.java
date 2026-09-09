package io.github.bacelardev.iphoneresale.application.port.storage;

public record StoredPhoto(
        String storageKey,
        String originalFilename,
        String mimeType,
        long sizeBytes
) {
}
