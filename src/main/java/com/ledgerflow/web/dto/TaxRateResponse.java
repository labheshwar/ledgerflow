package com.ledgerflow.web.dto;

import com.ledgerflow.domain.TaxRate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TaxRateResponse(
        Long id, String name, BigDecimal rate, boolean archived, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static TaxRateResponse from(TaxRate t) {
        return new TaxRateResponse(t.getId(), t.getName(), t.getRate(), t.isArchived(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
