package io.github.bacelardev.iphoneresale.web.dto.initialization;

import io.github.bacelardev.iphoneresale.domain.model.OwnerCapitalOpening;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.math.BigDecimal;

public record OwnerCapitalOpeningResponse(
        UserReference ownerUser,
        BigDecimal historicalContributionAmount,
        BigDecimal historicalWithdrawalAmount
) {
    public static OwnerCapitalOpeningResponse from(OwnerCapitalOpening opening) {
        return new OwnerCapitalOpeningResponse(
                UserReference.from(opening.getOwnerUser()),
                opening.getHistoricalContributionAmount(),
                opening.getHistoricalWithdrawalAmount()
        );
    }
}
