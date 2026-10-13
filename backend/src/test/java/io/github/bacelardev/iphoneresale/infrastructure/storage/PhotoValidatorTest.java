package io.github.bacelardev.iphoneresale.infrastructure.storage;

import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoValidatorTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    private static final byte[] WEBP = Base64.getDecoder().decode(
            "UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAgA0JaQAA3AA/vuUAAA=");

    @Test
    void validatesContentMimeExtensionAndDimensions() {
        ValidatedPhoto photo = PhotoValidator.validate(
                new MockMultipartFile("photo", "safe.png", "image/png", PNG));

        assertThat(photo.mimeType()).isEqualTo("image/png");
        assertThat(photo.extension()).isEqualTo(".png");
        assertThat(photo.content()).isEqualTo(PNG);

        ValidatedPhoto webp = PhotoValidator.validate(
                new MockMultipartFile("photo", "safe.webp", "image/webp", WEBP));
        assertThat(webp.mimeType()).isEqualTo("image/webp");
        assertThat(webp.extension()).isEqualTo(".webp");
    }

    @Test
    void rejectsMalformedAndMismatchedFiles() {
        assertCode(new MockMultipartFile("photo", "fake.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
                "UNSUPPORTED_IMAGE_TYPE");
        assertCode(new MockMultipartFile("photo", "wrong.jpg", "image/png", PNG),
                "UNSUPPORTED_IMAGE_TYPE");
        assertCode(new MockMultipartFile("photo", "safe.png", "image/jpeg", PNG),
                "UNSUPPORTED_IMAGE_TYPE");
        assertCode(new MockMultipartFile("photo", "fake.webp", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'}),
                "UNSUPPORTED_IMAGE_TYPE");
    }

    @Test
    void rejectsOversizedFilesBeforeProviderCall() {
        assertCode(new MockMultipartFile("photo", "large.png", "image/png",
                new byte[10 * 1024 * 1024 + 1]), "FILE_TOO_LARGE");
    }

    private static void assertCode(MockMultipartFile file, String code) {
        assertThatThrownBy(() -> PhotoValidator.validate(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
