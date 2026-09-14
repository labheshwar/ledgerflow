package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.exception.UnbalancedTransactionException;
import com.ledgerflow.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A journal entry ready to post.
 *
 * The balance check lives in the constructor rather than in the posting
 * service, which means an unbalanced journal entry is not a value that can
 * exist. There is nothing to pass around, log, half persist, or forget to
 * validate before use -- the only way to hold one of these is to have
 * satisfied double entry already.
 *
 * {@link JournalBuilder} is the readable way to construct one; it is an
 * ergonomic wrapper, not the enforcement, so this holds however the command
 * was made.
 *
 * @param txnDate the date the entry is effective for accounting, supplied by
 *        the caller -- routinely not today.
 */
public record PostingCommand(String idempotencyKey, String description, LocalDate txnDate, List<EntryLine> entries) {

    public PostingCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("A journal entry needs an idempotency key");
        }
        Objects.requireNonNull(txnDate, "txnDate");
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);

        if (entries.size() < 2) {
            // Double entry: every movement of value has a source and a
            // destination. One line is half a story.
            throw new UnbalancedTransactionException("A journal entry needs at least two lines");
        }

        String currency = entries.get(0).amount().currency();
        Money debits = Money.zero(currency);
        Money credits = Money.zero(currency);

        for (EntryLine line : entries) {
            Money amount = line.amount();
            if (!amount.isPositive()) {
                // Direction is carried by DEBIT/CREDIT. A negative amount
                // would make "credit -50" and "debit 50" two spellings of one
                // thing, and every report downstream would have to handle both.
                throw new UnbalancedTransactionException("An entry amount must be greater than zero, got " + amount);
            }
            if (!amount.currency().equals(currency)) {
                // Money.plus would throw on its own, but the caller deserves
                // to hear that their journal is wrong rather than that some
                // arithmetic failed.
                throw new UnbalancedTransactionException(
                        "A journal entry cannot mix currencies: found %s and %s"
                                .formatted(currency, amount.currency()));
            }
            if (line.entryType() == EntryType.DEBIT) {
                debits = debits.plus(amount);
            } else {
                credits = credits.plus(amount);
            }
        }

        if (debits.compareTo(credits) != 0) {
            throw new UnbalancedTransactionException("Debits (%s) must equal credits (%s)".formatted(debits, credits));
        }
    }

    /** The single currency every line is denominated in. */
    public String currency() {
        return entries.get(0).amount().currency();
    }
}
