package io.github.bacelardev.iphoneresale.infrastructure.storage;

import io.github.bacelardev.iphoneresale.application.port.storage.PhotoContent;
import io.github.bacelardev.iphoneresale.application.port.storage.PhotoStorage;
import io.github.bacelardev.iphoneresale.application.port.storage.StoredPhoto;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.config.properties.ImageKitProperties;
import io.github.bacelardev.iphoneresale.config.properties.PhotoStorageProperties;
import io.imagekit.client.ImageKitClient;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import io.imagekit.models.SrcOptions;
import io.imagekit.models.files.FileDeleteParams;
import io.imagekit.models.files.FileUploadParams;
import io.imagekit.models.files.FileUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.photo-storage.provider", havingValue = "imagekit")
public class ImageKitPhotoStorage implements PhotoStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageKitPhotoStorage.class);
    private static final String PREFIX = "imagekit:";

    private final ImageKitClient client;
    private final HttpClient http;
    private final String urlEndpoint;
    private final String folder;
    private final LocalFilePhotoStorage legacyLocalStorage;

    public ImageKitPhotoStorage(ImageKitProperties properties, PhotoStorageProperties localProperties) {
        this.client = ImageKitOkHttpClient.builder().privateKey(properties.requiredPrivateKey()).build();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.urlEndpoint = properties.requiredUrlEndpoint();
        this.folder = properties.normalizedFolder();
        this.legacyLocalStorage = new LocalFilePhotoStorage(localProperties);
    }

    @Override
    public StoredPhoto store(UUID deviceId, MultipartFile file) {
        ValidatedPhoto photo = PhotoValidator.validate(file);
        String generatedName = UUID.randomUUID() + photo.extension();
        String uploadedFileId = null;
        try {
            FileUploadResponse response = client.files().upload(FileUploadParams.builder()
                    .file(photo.content())
                    .fileName(generatedName)
                    .folder(folder + "/" + deviceId)
                    .isPrivateFile(true)
                    .build());
            String fileId = response.fileId().orElseThrow(() ->
                    new IllegalStateException("ImageKit response has no fileId"));
            uploadedFileId = fileId;
            String filePath = response.filePath().orElseThrow(() ->
                    new IllegalStateException("ImageKit response has no filePath"));
            String encodedPath = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(filePath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String key = PREFIX + fileId + ":" + encodedPath;
            if (key.length() > 512) throw new IllegalStateException("ImageKit storage key is too long");
            return new StoredPhoto(key, photo.originalFilename(), photo.mimeType(), photo.content().length);
        } catch (RuntimeException exception) {
            if (uploadedFileId != null) {
                try {
                    deleteUploaded(uploadedFileId);
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw unavailable("Não foi possível enviar a foto ao armazenamento.", exception);
        }
    }

    @Override
    public PhotoContent read(String storageKey, String mimeType, long sizeBytes) {
        if (!storageKey.startsWith(PREFIX)) {
            try {
                return legacyLocalStorage.read(storageKey, mimeType, sizeBytes);
            } catch (BusinessException exception) {
                if (!"PHOTO_CONTENT_NOT_FOUND".equals(exception.getCode())) throw exception;
                return readRemote("/" + storageKey, mimeType);
            }
        }
        return readRemote(parse(storageKey).filePath(), mimeType);
    }

    private PhotoContent readRemote(String filePath, String mimeType) {
        try {
            String signedUrl = client.helper().buildUrl(SrcOptions.builder()
                    .urlEndpoint(urlEndpoint)
                    .src(filePath)
                    .signed(true)
                    .expiresIn(300.0)
                    .build());
            HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ImageKit delivery returned HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body.length == 0 || body.length > 10L * 1024 * 1024) {
                throw new IllegalStateException("ImageKit delivery returned an invalid size");
            }
            return new PhotoContent(new ByteArrayResource(body), mimeType, body.length);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("Não foi possível ler a foto do armazenamento.", exception);
        } catch (Exception exception) {
            throw unavailable("Não foi possível ler a foto do armazenamento.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (!storageKey.startsWith(PREFIX)) {
            legacyLocalStorage.delete(storageKey);
            return;
        }
        try {
            deleteUploaded(parse(storageKey).fileId());
        } catch (RuntimeException exception) {
            LOGGER.warn("ImageKit cleanup failed for managed object; type={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void deleteUploaded(String fileId) {
        client.files().delete(FileDeleteParams.builder().fileId(fileId).build());
    }

    private static Key parse(String storageKey) {
        String[] tokens = storageKey.split(":", 3);
        if (tokens.length != 3 || tokens[1].isBlank() || tokens[2].isBlank()) {
            throw new IllegalArgumentException("Invalid ImageKit storage key");
        }
        try {
            String path = new String(Base64.getUrlDecoder().decode(tokens[2]),
                    java.nio.charset.StandardCharsets.UTF_8);
            if (!path.startsWith("/")) throw new IllegalArgumentException("Invalid ImageKit file path");
            return new Key(tokens[1], path);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid ImageKit storage key", exception);
        }
    }

    private static BusinessException unavailable(String message, Exception cause) {
        BusinessException result = new BusinessException(HttpStatus.BAD_GATEWAY,
                "PHOTO_STORAGE_UNAVAILABLE", message);
        result.initCause(cause);
        return result;
    }

    private record Key(String fileId, String filePath) {
    }
}
