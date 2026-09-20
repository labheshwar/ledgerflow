package com.ledgerflow.web.dto;

import com.ledgerflow.service.TaxRateDraft;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TaxRateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal rate) {

    public TaxRateDraft toDraft() {
        return new TaxRateDraft(name, rate);
    }
}
