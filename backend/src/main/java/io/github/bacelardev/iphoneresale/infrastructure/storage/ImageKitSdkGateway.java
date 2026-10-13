package io.github.bacelardev.iphoneresale.infrastructure.storage;

import io.github.bacelardev.iphoneresale.config.properties.ImageKitProperties;
import io.imagekit.client.ImageKitClient;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import io.imagekit.models.SrcOptions;
import io.imagekit.models.files.FileDeleteParams;
import io.imagekit.models.files.FileUploadParams;
import io.imagekit.models.files.FileUploadResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.photo-storage.provider", havingValue = "imagekit")
class ImageKitSdkGateway implements ImageKitGateway {

    private final ImageKitClient client;
    private final HttpClient http;
    private final String urlEndpoint;

    ImageKitSdkGateway(ImageKitProperties properties) {
        this.client = ImageKitOkHttpClient.builder().privateKey(properties.requiredPrivateKey()).build();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.urlEndpoint = properties.requiredUrlEndpoint();
    }

    @Override
    public Upload upload(byte[] content, String fileName, String folder) {
        FileUploadResponse response = client.files().upload(FileUploadParams.builder()
                .file(content)
                .fileName(fileName)
                .folder(folder)
                .isPrivateFile(true)
                .build());
        String fileId = response.fileId().orElseThrow(() ->
                new IllegalStateException("ImageKit response has no fileId"));
        String filePath = response.filePath().orElseThrow(() ->
                new IllegalStateException("ImageKit response has no filePath"));
        return new Upload(fileId, filePath);
    }

    @Override
    public byte[] download(String filePath, Duration signedUrlTtl, long maxBytes) {
        try {
            String signedUrl = client.helper().buildUrl(SrcOptions.builder()
                    .urlEndpoint(urlEndpoint)
                    .src(filePath)
                    .signed(true)
                    .expiresIn((double) signedUrlTtl.toSeconds())
                    .build());
            HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ImageKit delivery returned HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body.length == 0 || body.length > maxBytes) {
                throw new IllegalStateException("ImageKit delivery returned an invalid size");
            }
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ImageKit delivery interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("ImageKit delivery failed", exception);
        }
    }

    @Override
    public void delete(String fileId) {
        client.files().delete(FileDeleteParams.builder().fileId(fileId).build());
    }
}
