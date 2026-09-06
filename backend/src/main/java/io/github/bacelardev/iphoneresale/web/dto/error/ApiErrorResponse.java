package io.github.bacelardev.iphoneresale.web.dto.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        UUID requestId,
        List<FieldErrorResponse> fieldErrors
) {
}
