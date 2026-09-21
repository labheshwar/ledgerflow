package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.money.Money;
import java.math.BigDecimal;

/**
 * {@code rateOverride} is null for the ordinary case, in which
 * {@link PostingExecutor} looks up the rate itself for the transaction's own
 * date. A settlement relieving a receivable booked at an earlier date needs
 * that receivable's own frozen rate instead of a fresh one -- see
 * {@link JournalBuilder#creditAtRate} -- which is what this line exists to
 * carry.
 */
public record EntryLine(Long accountId, EntryType entryType, Money amount, BigDecimal rateOverride) {

    public EntryLine(Long accountId, EntryType entryType, Money amount) {
        this(accountId, entryType, amount, null);
    }
}
