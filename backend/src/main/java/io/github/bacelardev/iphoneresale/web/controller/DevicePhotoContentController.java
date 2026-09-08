package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.port.storage.PhotoContent;
import io.github.bacelardev.iphoneresale.application.port.storage.PhotoStorage;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.device.PhotoUrlSigner;
import io.github.bacelardev.iphoneresale.domain.model.DevicePhoto;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DevicePhotoJpaRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/device-photos/content")
public class DevicePhotoContentController {

    private final DevicePhotoJpaRepository photos;
    private final PhotoStorage storage;
    private final PhotoUrlSigner signer;

    public DevicePhotoContentController(
            DevicePhotoJpaRepository photos,
            PhotoStorage storage,
            PhotoUrlSigner signer
    ) {
        this.photos = photos;
        this.storage = storage;
        this.signer = signer;
    }

    @GetMapping("/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> content(
            @PathVariable UUID id,
            @RequestParam long expires,
            @RequestParam String signature
    ) {
        signer.verify(id, expires, signature);
        DevicePhoto photo = photos.findByIdAndRemovedAtIsNull(id).orElseThrow(() ->
                BusinessException.notFound("PHOTO_NOT_FOUND", "Foto não encontrada."));
        PhotoContent content = storage.read(photo.getStorageKey(), photo.getMimeType(), photo.getSizeBytes());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.sizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(4)).cachePrivate())
                .body(content.resource());
    }
}
