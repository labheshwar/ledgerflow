package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Item;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ItemResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal defaultUnitPrice,
        Long defaultTaxRateId,
        boolean archived,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ItemResponse from(Item i) {
        return new ItemResponse(
                i.getId(),
                i.getSku(),
                i.getName(),
                i.getDescription(),
                i.getDefaultUnitPrice(),
                i.getDefaultTaxRateId(),
                i.isArchived(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }
}
