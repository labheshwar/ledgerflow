package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * An entry with its running balance folded in, computed on read by replaying
 * the account in accounting order.
 */
public record LedgerEntry(
        Long id,
        Long transactionId,
        EntryType direction,
        BigDecimal amount,
        BigDecimal runningBalance,
        LocalDate txnDate,
        OffsetDateTime createdAt) {
}
