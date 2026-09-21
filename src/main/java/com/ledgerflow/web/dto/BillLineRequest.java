package com.ledgerflow.web.dto;

import com.ledgerflow.service.BillLineDraft;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillLineRequest(
        @NotNull Long accountId,
        Long itemId,
        @NotBlank @Size(max = 500) String description,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        Long taxRateId) {

    public BillLineDraft toDraft() {
        return new BillLineDraft(accountId, itemId, description, quantity, unitPrice, taxRateId);
    }
}
