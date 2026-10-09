package io.github.bacelardev.iphoneresale.infrastructure.storage;

import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PhotoValidator {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int MAX_DIMENSION = 12_000;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp"
    );
    private static final Map<String, Set<String>> ACCEPTED_SUFFIXES = Map.of(
            "image/jpeg", Set.of(".jpg", ".jpeg"),
            "image/png", Set.of(".png"),
            "image/webp", Set.of(".webp")
    );

    private PhotoValidator() {
    }

    static ValidatedPhoto validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("INVALID_PHOTO", "A foto não pode estar vazia.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_TOO_LARGE", "Cada imagem deve possuir no máximo 10 MiB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw BusinessException.badRequest("INVALID_PHOTO", "Não foi possível ler a foto.");
        }
        String detected = detect(content);
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String filename = cleanFilename(file.getOriginalFilename());
        if (detected == null || !detected.equals(declared) || !validExtension(filename, detected)) {
            unsupported();
        }
        if ("image/webp".equals(detected)) {
            validateWebpDimensions(content);
        } else {
            validateDimensions(content);
        }
        return new ValidatedPhoto(content, filename, detected, EXTENSIONS.get(detected));
    }

    private static void validateDimensions(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) unsupported();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                requireSafeDimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            unsupported();
        }
    }

    private static void validateWebpDimensions(byte[] content) {
        if (content.length < 25 || littleEndian32(content, 4) + 8 != content.length) unsupported();
        String chunk = new String(content, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8 " -> validateVp8Chunk(content, 12);
            case "VP8L" -> validateVp8lChunk(content, 12);
            case "VP8X" -> {
                int size = chunkSize(content, 12, 10);
                requireSafeDimensions(1 + littleEndian24(content, 24),
                        1 + littleEndian24(content, 27));
                requireExtendedImageChunk(content, nextChunkOffset(12, size));
            }
            default -> unsupported();
        }
    }

    private static void validateVp8Chunk(byte[] content, int chunkOffset) {
        chunkSize(content, chunkOffset, 10);
        int payload = chunkOffset + 8;
        if ((content[payload + 3] & 0xff) != 0x9d || (content[payload + 4] & 0xff) != 0x01
                || (content[payload + 5] & 0xff) != 0x2a) unsupported();
        requireSafeDimensions(littleEndian16(content, payload + 6) & 0x3fff,
                littleEndian16(content, payload + 8) & 0x3fff);
    }

    private static void validateVp8lChunk(byte[] content, int chunkOffset) {
        chunkSize(content, chunkOffset, 5);
        int payload = chunkOffset + 8;
        if ((content[payload] & 0xff) != 0x2f) unsupported();
        int b0 = content[payload + 1] & 0xff;
        int b1 = content[payload + 2] & 0xff;
        int b2 = content[payload + 3] & 0xff;
        int b3 = content[payload + 4] & 0xff;
        requireSafeDimensions(1 + b0 + ((b1 & 0x3f) << 8),
                1 + ((b1 & 0xc0) >> 6) + (b2 << 2) + ((b3 & 0x0f) << 10));
    }

    private static void requireExtendedImageChunk(byte[] content, int offset) {
        while (offset + 8 <= content.length) {
            String chunk = new String(content, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = chunkSize(content, offset, 0);
            if ("VP8 ".equals(chunk)) {
                validateVp8Chunk(content, offset);
                return;
            }
            if ("VP8L".equals(chunk)) {
                validateVp8lChunk(content, offset);
                return;
            }
            offset = nextChunkOffset(offset, size);
        }
        unsupported();
    }

    private static int chunkSize(byte[] content, int offset, int minimum) {
        if (offset < 0 || offset + 8 > content.length) unsupported();
        long size = littleEndian32(content, offset + 4);
        if (size < minimum || size > Integer.MAX_VALUE || offset + 8L + size > content.length) unsupported();
        return (int) size;
    }

    private static int nextChunkOffset(int offset, int size) {
        return offset + 8 + size + (size & 1);
    }

    private static int littleEndian16(byte[] content, int offset) {
        return (content[offset] & 0xff) | ((content[offset + 1] & 0xff) << 8);
    }

    private static int littleEndian24(byte[] content, int offset) {
        return (content[offset] & 0xff) | ((content[offset + 1] & 0xff) << 8)
                | ((content[offset + 2] & 0xff) << 16);
    }

    private static long littleEndian32(byte[] content, int offset) {
        return (content[offset] & 0xffL) | ((content[offset + 1] & 0xffL) << 8)
                | ((content[offset + 2] & 0xffL) << 16) | ((content[offset + 3] & 0xffL) << 24);
    }

    private static void requireSafeDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_PIXELS) {
            throw BusinessException.unprocessable("IMAGE_DIMENSIONS_EXCEEDED",
                    "A imagem excede o limite seguro de dimensões.");
        }
    }

    private static boolean validExtension(String filename, String mimeType) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return ACCEPTED_SUFFIXES.getOrDefault(mimeType, Set.of()).stream().anyMatch(lower::endsWith);
    }

    private static String detect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return "image/png";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }

    private static String cleanFilename(String raw) {
        if (raw == null || raw.isBlank()) return "photo";
        String cleaned = Path.of(raw.replace('\\', '/')).getFileName().toString()
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) return "photo";
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
    }

    private static void unsupported() {
        throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE",
                "Envie uma imagem JPEG, PNG ou WebP válida, com extensão compatível.");
    }
}
