package com.ledgerflow.events;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A journal entry that has been committed to the ledger.
 *
 * The whole posting travels in one event rather than one event per line.
 * A double-entry transaction is only meaningful as a set -- a consumer that
 * saw three of four legs would compute an unbalanced view of the world and
 * have no way to know it was waiting for more.
 */
public record TransactionPostedEvent(
        Long transactionId,
        String idempotencyKey,
        String description,
        OffsetDateTime postedAt,
        List<Line> entries) {

    public record Line(Long accountId, String accountName, String entryType, BigDecimal amount) {}
}
