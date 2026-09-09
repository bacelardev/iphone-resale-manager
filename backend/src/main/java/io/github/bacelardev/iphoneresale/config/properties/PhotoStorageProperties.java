package io.github.bacelardev.iphoneresale.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.photo-storage")
public record PhotoStorageProperties(
        @NotBlank String root,
        @NotBlank @Size(min = 32) String signingSecret,
        @NotNull Duration signedUrlTtl
) {
    public PhotoStorageProperties {
        if (signedUrlTtl != null && (signedUrlTtl.isZero() || signedUrlTtl.isNegative())) {
            throw new IllegalArgumentException("app.photo-storage.signed-url-ttl must be positive");
        }
    }
}
