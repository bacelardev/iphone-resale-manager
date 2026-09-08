package io.github.bacelardev.iphoneresale.infrastructure.storage;

import io.github.bacelardev.iphoneresale.application.port.storage.PhotoContent;
import io.github.bacelardev.iphoneresale.application.port.storage.PhotoStorage;
import io.github.bacelardev.iphoneresale.application.port.storage.StoredPhoto;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.config.properties.PhotoStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalFilePhotoStorage implements PhotoStorage {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp"
    );

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
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("INVALID_PHOTO", "A foto não pode estar vazia.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_TOO_LARGE", "Cada imagem deve possuir no máximo 10 MiB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw BusinessException.badRequest("INVALID_PHOTO", "Não foi possível ler a foto.");
        }
        String detected = detect(content);
        String declared = file.getContentType();
        if (detected == null || !detected.equals(declared) || !EXTENSIONS.containsKey(detected)) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_IMAGE_TYPE", "Envie uma imagem JPEG, PNG ou WebP válida.");
        }
        String key = deviceId + "/" + UUID.randomUUID() + EXTENSIONS.get(detected);
        Path target = resolve(key);
        Path temporary = target.resolveSibling(target.getFileName() + ".uploading");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            tryDelete(temporary);
            throw new IllegalStateException("Unable to store photo", exception);
        }
        return new StoredPhoto(key, cleanFilename(file.getOriginalFilename()), detected, content.length);
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

    private static String detect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String cleanFilename(String raw) {
        if (raw == null || raw.isBlank()) return "photo";
        String cleaned = Path.of(raw.replace('\\', '/')).getFileName().toString()
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) return "photo";
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort compensation. The random key is never reused.
        }
    }
}
