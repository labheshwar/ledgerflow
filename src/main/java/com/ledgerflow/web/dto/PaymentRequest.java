package com.ledgerflow.web.dto;

import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.service.PaymentDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentRequest(
        @NotNull Long contactId,
        @NotNull PaymentDirection direction,
        @NotNull LocalDate paymentDate,
        @NotNull @Positive BigDecimal amount,
        @Size(max = 1000) String notes,
        @Valid List<PaymentAllocationRequest> allocations,
        Long bankAccountId) {

    public PaymentDraft toDraft() {
        return new PaymentDraft(
                contactId,
                direction,
                paymentDate,
                amount,
                notes,
                allocations == null ? List.of() : allocations.stream().map(PaymentAllocationRequest::toDraft).toList(),
                bankAccountId);
    }
}
