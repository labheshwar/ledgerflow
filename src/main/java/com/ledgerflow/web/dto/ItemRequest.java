package com.ledgerflow.web.dto;

import com.ledgerflow.service.ItemDraft;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ItemRequest(
        @Size(max = 50) String sku,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String description,
        @DecimalMin("0") BigDecimal defaultUnitPrice,
        Long defaultTaxRateId) {

    public ItemDraft toDraft() {
        return new ItemDraft(sku, name, description, defaultUnitPrice, defaultTaxRateId);
    }
}
