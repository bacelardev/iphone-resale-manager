package io.github.bacelardev.iphoneresale.web.dto.maintenance;

import io.github.bacelardev.iphoneresale.domain.model.MaintenanceItem;
import io.github.bacelardev.iphoneresale.web.dto.common.CatalogReference;

import java.math.BigDecimal;
import java.util.UUID;

public record MaintenanceItemResponse(
        UUID id,
        CatalogReference part,
        String details,
        BigDecimal cost,
        int position
) {
    public static MaintenanceItemResponse from(MaintenanceItem item) {
        return new MaintenanceItemResponse(
                item.getId(),
                new CatalogReference(item.getPart().getId(), item.getPart().getCode(), item.getPart().getName()),
                item.getDetails(), item.getCost(), item.getPosition()
        );
    }
}
