package io.github.bacelardev.iphoneresale.application.port.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface PhotoStorage {

    StoredPhoto store(UUID deviceId, MultipartFile file);

    PhotoContent read(String storageKey, String mimeType, long sizeBytes);

    void delete(String storageKey);
}
