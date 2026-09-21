package com.ledgerflow.service;

import com.ledgerflow.domain.PaymentDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What it takes to record a payment -- there is no separate "edit" shape, since a payment is never edited.
 *
 * {@code bankAccountId} is null for the ordinary case, in which cash posts
 * to the organization's one system CASH account exactly as it always has.
 * The reconciliation workspace is the one caller that sets it, so a
 * payment settling a statement line posts its cash side to that specific
 * bank account's own ledger account instead -- see {@code PaymentService}.
 */
public record PaymentDraft(
        Long contactId,
        PaymentDirection direction,
        LocalDate paymentDate,
        BigDecimal amount,
        String notes,
        List<PaymentAllocationDraft> allocations,
        Long bankAccountId) {

    public PaymentDraft(
            Long contactId,
            PaymentDirection direction,
            LocalDate paymentDate,
            BigDecimal amount,
            String notes,
            List<PaymentAllocationDraft> allocations) {
        this(contactId, direction, paymentDate, amount, notes, allocations, null);
    }
}
