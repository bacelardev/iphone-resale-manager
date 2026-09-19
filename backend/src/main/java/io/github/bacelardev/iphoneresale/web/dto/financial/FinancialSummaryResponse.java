package io.github.bacelardev.iphoneresale.web.dto.financial;

import java.math.BigDecimal;
import java.time.Instant;

public record FinancialSummaryResponse(
        FinancialPeriodResponse period,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal revenue,
        BigDecimal devicePurchaseCost,
        BigDecimal maintenanceCost,
        BigDecimal profit,
        BigDecimal marginPercent,
        BigDecimal stockCapital,
        Instant calculatedAt
) {
}
