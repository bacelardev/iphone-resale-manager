package io.github.bacelardev.iphoneresale.web.dto.financial;

import java.time.Instant;

public record FinancialPeriodResponse(
        Instant from,
        Instant to,
        String businessTimezone
) {
}
