package com.ledgerflow.web.dto;

import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.service.PaymentAllocationDraft;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PaymentAllocationRequest(
        @NotNull DocumentType documentType, @NotNull Long documentId, @NotNull @Positive BigDecimal amount) {

    public PaymentAllocationDraft toDraft() {
        return new PaymentAllocationDraft(documentType, documentId, amount);
    }
}
