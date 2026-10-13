package io.github.bacelardev.iphoneresale.infrastructure.storage;

import io.github.bacelardev.iphoneresale.application.port.storage.PhotoContent;
import io.github.bacelardev.iphoneresale.application.port.storage.StoredPhoto;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.config.properties.ImageKitProperties;
import io.github.bacelardev.iphoneresale.config.properties.PhotoStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageKitPhotoStorageTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path root;

    @Test
    void uploadsValidatedPrivateObjectAndKeepsProviderIdentifiers() {
        FakeGateway gateway = new FakeGateway();
        UUID deviceId = UUID.randomUUID();
        ImageKitPhotoStorage storage = storage(gateway);

        StoredPhoto stored = storage.store(deviceId,
                new MockMultipartFile("photo", "original.png", "image/png", PNG));

        assertThat(gateway.uploadedContent).isEqualTo(PNG);
        assertThat(gateway.uploadedName).endsWith(".png");
        assertThat(gateway.uploadedFolder).isEqualTo("/delarte/devices/" + deviceId);
        assertThat(stored.storageKey()).startsWith("imagekit:file-1:");
        assertThat(stored.originalFilename()).isEqualTo("original.png");
        assertThat(stored.mimeType()).isEqualTo("image/png");
        assertThat(stored.sizeBytes()).isEqualTo(PNG.length);
    }

    @Test
    void downloadsManagedObjectThroughFiveMinuteSignedDeliveryContract() throws IOException {
        FakeGateway gateway = new FakeGateway();
        ImageKitPhotoStorage storage = storage(gateway);
        StoredPhoto stored = storage.store(UUID.randomUUID(),
                new MockMultipartFile("photo", "photo.png", "image/png", PNG));

        PhotoContent content = storage.read(stored.storageKey(), "image/png", PNG.length);

        assertThat(content.resource().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(gateway.downloadedPath).isEqualTo("/delarte/devices/file-1.png");
        assertThat(gateway.signedUrlTtl).isEqualTo(Duration.ofMinutes(5));
        assertThat(gateway.maxDownloadBytes).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void removesManagedObjectByProviderFileId() {
        FakeGateway gateway = new FakeGateway();
        ImageKitPhotoStorage storage = storage(gateway);
        StoredPhoto stored = storage.store(UUID.randomUUID(),
                new MockMultipartFile("photo", "photo.png", "image/png", PNG));

        storage.delete(stored.storageKey());

        assertThat(gateway.deletedFileId).isEqualTo("file-1");
    }

    @Test
    void convertsProviderFailureToStableStorageError() {
        FakeGateway gateway = new FakeGateway();
        gateway.uploadFailure = new IllegalStateException("provider detail");

        assertThatThrownBy(() -> storage(gateway).store(UUID.randomUUID(),
                new MockMultipartFile("photo", "photo.png", "image/png", PNG)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PHOTO_STORAGE_UNAVAILABLE");
                    assertThat(exception.getMessage()).doesNotContain("provider detail");
                });
    }

    @Test
    void compensatesUploadWhenPersistableStorageKeyCannotBeBuilt() {
        FakeGateway gateway = new FakeGateway();
        gateway.filePath = "/" + "a".repeat(500);

        assertThatThrownBy(() -> storage(gateway).store(UUID.randomUUID(),
                new MockMultipartFile("photo", "photo.png", "image/png", PNG)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PHOTO_STORAGE_UNAVAILABLE"));
        assertThat(gateway.deletedFileId).isEqualTo("file-1");
    }

    @Test
    void preservesLegacyLocalFileAndUsesRemoteFallbackWithoutPrematureDeletion() throws IOException {
        FakeGateway gateway = new FakeGateway();
        ImageKitPhotoStorage storage = storage(gateway);
        Path local = root.resolve("device/photo.png");
        Files.createDirectories(local.getParent());
        Files.write(local, PNG);

        PhotoContent localContent = storage.read("device/photo.png", "image/png", PNG.length);
        assertThat(localContent.resource().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(gateway.downloadedPath).isNull();

        PhotoContent migratedContent = storage.read("device/migrated.png", "image/png", PNG.length);
        assertThat(migratedContent.resource().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(gateway.downloadedPath).isEqualTo("/device/migrated.png");
        assertThat(Files.exists(local)).isTrue();
    }

    private ImageKitPhotoStorage storage(FakeGateway gateway) {
        return new ImageKitPhotoStorage(
                gateway,
                new ImageKitProperties("unused", "https://ik.imagekit.io/example", "/delarte/devices"),
                new PhotoStorageProperties(root.toString(), "s".repeat(32), Duration.ofMinutes(5))
        );
    }

    private static final class FakeGateway implements ImageKitGateway {
        private byte[] uploadedContent;
        private String uploadedName;
        private String uploadedFolder;
        private String filePath = "/delarte/devices/file-1.png";
        private RuntimeException uploadFailure;
        private String downloadedPath;
        private Duration signedUrlTtl;
        private long maxDownloadBytes;
        private String deletedFileId;

        @Override
        public Upload upload(byte[] content, String fileName, String folder) {
            if (uploadFailure != null) throw uploadFailure;
            uploadedContent = content;
            uploadedName = fileName;
            uploadedFolder = folder;
            return new Upload("file-1", filePath);
        }

        @Override
        public byte[] download(String path, Duration ttl, long maxBytes) {
            downloadedPath = path;
            signedUrlTtl = ttl;
            maxDownloadBytes = maxBytes;
            return PNG;
        }

        @Override
        public void delete(String fileId) {
            deletedFileId = fileId;
        }
    }
}
