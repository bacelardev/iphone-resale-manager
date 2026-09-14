package io.github.bacelardev.iphoneresale.web.dto.financial;

import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancialTransactionResponse(
        UUID id,
        String direction,
        String type,
        BigDecimal amount,
        Instant occurredAt,
        String description,
        UserReference ownerUser,
        FinancialSourceResponse source,
        UUID reversalOfId,
        Instant createdAt,
        UserReference createdBy
) {
    public static FinancialTransactionResponse from(FinancialTransaction transaction) {
        return new FinancialTransactionResponse(
                transaction.getId(),
                transaction.getDirection().name(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                transaction.getDescription(),
                UserReference.from(transaction.getOwnerUser()),
                source(transaction),
                transaction.getReversalOf() == null ? null : transaction.getReversalOf().getId(),
                transaction.getCreatedAt(),
                UserReference.from(transaction.getCreatedBy())
        );
    }

    private static FinancialSourceResponse source(FinancialTransaction transaction) {
        if (transaction.getDevice() != null) {
            return new FinancialSourceResponse(
                    "DEVICE", transaction.getDevice().getId(), transaction.getDevice().getId(),
                    transaction.getDevice().getInternalCode()
            );
        }
        if (transaction.getMaintenance() != null) {
            return new FinancialSourceResponse(
                    "MAINTENANCE", transaction.getMaintenance().getId(),
                    transaction.getMaintenance().getDevice().getId(),
                    transaction.getMaintenance().getDevice().getInternalCode()
            );
        }
        if (transaction.getSale() != null) {
            return new FinancialSourceResponse(
                    "SALE", transaction.getSale().getId(),
                    transaction.getSale().getDevice().getId(),
                    transaction.getSale().getDevice().getInternalCode()
            );
        }
        return null;
    }
}
