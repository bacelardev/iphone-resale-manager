package io.github.bacelardev.iphoneresale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.photo-storage.imagekit")
public record ImageKitProperties(
        String privateKey,
        String urlEndpoint,
        String folder
) {
    public String requiredPrivateKey() {
        return required(privateKey, "APP_IMAGEKIT_PRIVATE_KEY");
    }

    public String requiredUrlEndpoint() {
        String value = required(urlEndpoint, "APP_IMAGEKIT_URL_ENDPOINT");
        if (!value.startsWith("https://")) {
            throw new IllegalArgumentException("APP_IMAGEKIT_URL_ENDPOINT must use HTTPS");
        }
        return value.replaceAll("/+$", "");
    }

    public String normalizedFolder() {
        String value = folder == null || folder.isBlank() ? "/delarte/devices" : folder.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private static String required(String value, String variable) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(variable + " is required");
        return value;
    }

    @Override
    public String toString() {
        return "ImageKitProperties{privateKey='[redacted]', urlEndpoint='" + urlEndpoint
                + "', folder='" + folder + "'}";
    }
}
