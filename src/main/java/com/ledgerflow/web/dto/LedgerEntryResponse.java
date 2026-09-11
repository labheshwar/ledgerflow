package com.ledgerflow.web.dto;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.service.LedgerEntry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LedgerEntryResponse(
        Long id,
        Long transactionId,
        EntryType direction,
        BigDecimal amount,
        BigDecimal runningBalance,
        OffsetDateTime createdAt) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.id(), entry.transactionId(), entry.direction(), entry.amount(), entry.runningBalance(),
                entry.createdAt());
    }
}
