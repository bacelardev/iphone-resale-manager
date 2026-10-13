package io.github.bacelardev.iphoneresale.infrastructure.storage;

import java.time.Duration;

interface ImageKitGateway {

    Upload upload(byte[] content, String fileName, String folder);

    byte[] download(String filePath, Duration signedUrlTtl, long maxBytes);

    void delete(String fileId);

    record Upload(String fileId, String filePath) {
    }
}
