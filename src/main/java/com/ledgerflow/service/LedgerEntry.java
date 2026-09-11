package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An account's entry with its running balance folded in -- the account
 * itself only stores the current total, not a snapshot after each entry,
 * so this is computed on read rather than stored.
 */
public record LedgerEntry(
        Long id,
        Long transactionId,
        EntryType direction,
        BigDecimal amount,
        BigDecimal runningBalance,
        OffsetDateTime createdAt) {
}
