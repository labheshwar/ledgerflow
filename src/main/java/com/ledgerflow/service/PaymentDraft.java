package com.ledgerflow.service;

import com.ledgerflow.domain.PaymentDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What it takes to record a payment -- there is no separate "edit" shape, since a payment is never edited. */
public record PaymentDraft(
        Long contactId,
        PaymentDirection direction,
        LocalDate paymentDate,
        BigDecimal amount,
        String notes,
        List<PaymentAllocationDraft> allocations) {}
