package io.github.bacelardev.iphoneresale.application.service.device;

import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.config.properties.PhotoStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

@Component
public class PhotoUrlSigner {

    private final byte[] secret;
    private final long ttlSeconds;
    private final Clock clock;

    public PhotoUrlSigner(PhotoStorageProperties properties, Clock clock) {
        this.secret = properties.signingSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = properties.signedUrlTtl().toSeconds();
        this.clock = clock;
    }

    public String sign(UUID photoId) {
        long expires = clock.instant().getEpochSecond() + ttlSeconds;
        return UriComponentsBuilder.fromPath("/api/v1/device-photos/content/{id}")
                .queryParam("expires", expires)
                .queryParam("signature", signature(photoId, expires))
                .buildAndExpand(photoId)
                .toUriString();
    }

    public void verify(UUID photoId, long expires, String supplied) {
        if (expires < clock.instant().getEpochSecond() || supplied == null) {
            throw BusinessException.notFound("PHOTO_URL_INVALID", "A URL da foto expirou ou é inválida.");
        }
        byte[] expected = signature(photoId, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = supplied.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw BusinessException.notFound("PHOTO_URL_INVALID", "A URL da foto expirou ou é inválida.");
        }
    }

    private String signature(UUID photoId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] value = mac.doFinal((photoId + ":" + expires).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign photo URL", exception);
        }
    }
}
