package io.github.bacelardev.iphoneresale.infrastructure.storage;

import io.github.bacelardev.iphoneresale.application.port.storage.PhotoContent;
import io.github.bacelardev.iphoneresale.application.port.storage.PhotoStorage;
import io.github.bacelardev.iphoneresale.application.port.storage.StoredPhoto;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.config.properties.PhotoStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.photo-storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFilePhotoStorage implements PhotoStorage {

    private final Path root;

    public LocalFilePhotoStorage(PhotoStorageProperties properties) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize photo storage", exception);
        }
    }

    @Override
    public StoredPhoto store(UUID deviceId, MultipartFile file) {
        ValidatedPhoto photo = PhotoValidator.validate(file);
        String key = deviceId + "/" + UUID.randomUUID() + photo.extension();
        Path target = resolve(key);
        Path temporary = target.resolveSibling(target.getFileName() + ".uploading");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, photo.content());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            tryDelete(temporary);
            throw new IllegalStateException("Unable to store photo", exception);
        }
        return new StoredPhoto(key, photo.originalFilename(), photo.mimeType(), photo.content().length);
    }

    @Override
    public PhotoContent read(String storageKey, String mimeType, long sizeBytes) {
        Path file = resolve(storageKey);
        if (!Files.isRegularFile(file)) {
            throw BusinessException.notFound("PHOTO_CONTENT_NOT_FOUND", "Conteúdo da foto não encontrado.");
        }
        return new PhotoContent(new FileSystemResource(file), mimeType, sizeBytes);
    }

    @Override
    public void delete(String storageKey) {
        tryDelete(resolve(storageKey));
    }

    private Path resolve(String key) {
        Path candidate = root.resolve(key).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return candidate;
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort compensation. The random key is never reused.
        }
    }
}
