package com.ledgerflow.web.dto;

import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.PaymentAllocation;
import java.math.BigDecimal;

public record PaymentAllocationResponse(Long id, DocumentType documentType, Long documentId, BigDecimal amount) {

    public static PaymentAllocationResponse from(PaymentAllocation allocation) {
        return new PaymentAllocationResponse(
                allocation.getId(), allocation.getDocumentType(), allocation.getDocumentId(), allocation.getAmount());
    }
}
