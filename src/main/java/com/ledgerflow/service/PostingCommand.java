package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.exception.UnbalancedTransactionException;
import com.ledgerflow.money.Money;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A journal entry ready to post.
 *
 * The balance check lives in the constructor rather than in the posting
 * service, which means an unbalanced journal entry is not a value that can
 * exist -- for a single-currency journal, which is every journal this
 * project posted before milestone 15. There is nothing to pass around, log,
 * half persist, or forget to validate before use -- the only way to hold
 * one of these is to have satisfied double entry already.
 *
 * A foreign-currency settlement's own journal genuinely mixes currencies
 * (the cash and receivable legs in the document's own currency, an FX
 * gain/loss leg in the organization's base currency), and balancing that
 * needs an exchange rate this record has no way to look up -- it is a
 * plain value type, deliberately without database access. For that case
 * only, this constructor checks everything it still can (at least two
 * lines, every amount positive) and defers the balance check itself to
 * {@link PostingExecutor}, which runs inside the same database transaction
 * as every entry it would write -- an unbalanced mixed-currency command
 * still can never be committed, it is just discovered a moment later than a
 * single-currency one is.
 *
 * {@link JournalBuilder} is the readable way to construct one; it is an
 * ergonomic wrapper, not the enforcement, so this holds however the command
 * was made.
 *
 * @param txnDate the date the entry is effective for accounting, supplied by
 *        the caller -- routinely not today.
 * @param reversalOfTransactionId the transaction this journal undoes, or
 *        null for an ordinary posting. Not part of the balance invariant --
 *        a reversal has to balance for exactly the same reason any other
 *        journal does, no special case needed.
 */
public record PostingCommand(
        String idempotencyKey,
        String description,
        LocalDate txnDate,
        List<EntryLine> entries,
        Long reversalOfTransactionId) {

    public PostingCommand(String idempotencyKey, String description, LocalDate txnDate, List<EntryLine> entries) {
        this(idempotencyKey, description, txnDate, entries, null);
    }

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

        for (EntryLine line : entries) {
            if (!line.amount().isPositive()) {
                // Direction is carried by DEBIT/CREDIT. A negative amount
                // would make "credit -50" and "debit 50" two spellings of one
                // thing, and every report downstream would have to handle both.
                throw new UnbalancedTransactionException("An entry amount must be greater than zero, got " + line.amount());
            }
        }

        Set<String> currencies = new HashSet<>();
        for (EntryLine line : entries) {
            currencies.add(line.amount().currency());
        }
        // A mixed-currency command's balance cannot be validated here --
        // see this class's own Javadoc for why, and where the check
        // actually happens instead.
        if (currencies.size() == 1) {
            String currency = currencies.iterator().next();
            Money debits = Money.zero(currency);
            Money credits = Money.zero(currency);
            for (EntryLine line : entries) {
                if (line.entryType() == EntryType.DEBIT) {
                    debits = debits.plus(line.amount());
                } else {
                    credits = credits.plus(line.amount());
                }
            }

            if (debits.compareTo(credits) != 0) {
                throw new UnbalancedTransactionException("Debits (%s) must equal credits (%s)".formatted(debits, credits));
            }
        }
    }

    /**
     * The currency every line is denominated in, for a single-currency
     * journal. For a mixed-currency one this is only the first line's own
     * currency -- an approximation, fine for the event metadata that is
     * this method's only caller, not a fact to build logic on.
     */
    public String currency() {
        return entries.get(0).amount().currency();
    }
}
