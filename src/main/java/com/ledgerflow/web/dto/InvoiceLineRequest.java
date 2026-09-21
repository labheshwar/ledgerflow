package com.ledgerflow.web.dto;

import com.ledgerflow.service.InvoiceLineDraft;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InvoiceLineRequest(
        Long itemId,
        @NotBlank @Size(max = 500) String description,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        Long taxRateId) {

    public InvoiceLineDraft toDraft() {
        return new InvoiceLineDraft(itemId, description, quantity, unitPrice, taxRateId);
    }
}
