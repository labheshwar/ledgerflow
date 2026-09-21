package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentAllocation;
import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PaymentResponse(
        Long id,
        Long contactId,
        String contactName,
        PaymentDirection direction,
        PaymentStatus status,
        LocalDate paymentDate,
        BigDecimal amount,
        String currency,
        String notes,
        Long bankAccountId,
        String bankAccountName,
        Long postedTransactionId,
        OffsetDateTime createdAt,
        List<PaymentAllocationResponse> allocations) {

    public static PaymentResponse from(
            Payment payment, String contactName, String bankAccountName, List<PaymentAllocation> allocations) {
        return new PaymentResponse(
                payment.getId(),
                payment.getContactId(),
                contactName,
                payment.getDirection(),
                payment.getStatus(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getNotes(),
                payment.getBankAccountId(),
                bankAccountName,
                payment.getPostedTransactionId(),
                payment.getCreatedAt(),
                allocations.stream().map(PaymentAllocationResponse::from).toList());
    }
}
