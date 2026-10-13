package io.github.bacelardev.iphoneresale.infrastructure.storage;

record ValidatedPhoto(byte[] content, String originalFilename, String mimeType, String extension) {
}
