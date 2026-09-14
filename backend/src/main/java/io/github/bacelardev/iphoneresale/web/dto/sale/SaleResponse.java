package io.github.bacelardev.iphoneresale.web.dto.sale;

import io.github.bacelardev.iphoneresale.domain.enums.SaleStatus;
import io.github.bacelardev.iphoneresale.domain.model.Sale;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        UUID deviceId,
        BigDecimal salePrice,
        Instant soldAt,
        UserReference responsibleUser,
        SaleStatus status,
        BigDecimal purchasePrice,
        BigDecimal maintenanceTotal,
        BigDecimal investmentTotal,
        BigDecimal profit,
        BigDecimal marginPercent,
        Instant cancelledAt,
        UserReference cancelledBy,
        String cancellationReason,
        Instant createdAt,
        long version
) {
    public static SaleResponse from(Sale sale, BigDecimal maintenanceTotal) {
        BigDecimal purchasePrice = sale.getDevice().getPurchasePrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal maintenance = maintenanceTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal investment = purchasePrice.add(maintenance).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = sale.getSalePrice().subtract(investment).setScale(2, RoundingMode.HALF_UP);
        BigDecimal margin = profit.multiply(BigDecimal.valueOf(100))
                .divide(sale.getSalePrice(), 4, RoundingMode.HALF_UP);
        return new SaleResponse(
                sale.getId(), sale.getDevice().getId(), sale.getSalePrice(), sale.getSoldAt(),
                UserReference.from(sale.getResponsibleUser()), sale.getStatus(), purchasePrice,
                maintenance, investment, profit, margin, sale.getCancelledAt(),
                UserReference.from(sale.getCancelledBy()), sale.getCancellationReason(),
                sale.getCreatedAt(), sale.getVersion() == null ? 0 : sale.getVersion()
        );
    }
}
