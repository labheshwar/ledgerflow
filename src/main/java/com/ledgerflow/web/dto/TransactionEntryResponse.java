package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import java.math.BigDecimal;

public record TransactionEntryResponse(
        Long accountId,
        String accountName,
        EntryType direction,
        BigDecimal amount,
        String currency,
        BigDecimal baseAmount) {

    public static TransactionEntryResponse from(Entry entry) {
        return new TransactionEntryResponse(
                entry.getAccount().getId(),
                entry.getAccount().getName(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getBaseAmount());
    }
}
