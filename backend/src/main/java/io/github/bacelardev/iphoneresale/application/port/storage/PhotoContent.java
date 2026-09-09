package io.github.bacelardev.iphoneresale.application.port.storage;

import org.springframework.core.io.Resource;

public record PhotoContent(Resource resource, String mimeType, long sizeBytes) {
}
