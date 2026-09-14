package io.github.bacelardev.iphoneresale.web.dto.financial;

import java.util.UUID;

public record FinancialSourceResponse(
        String type,
        UUID id,
        UUID deviceId,
        String reference
) {
}
