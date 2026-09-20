package com.ledgerflow.service;

import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.money.Money;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Undoes a posting without ever mutating or deleting one.
 *
 * A reversal is an ordinary journal entry: the same accounts, the same
 * amounts, every DEBIT and CREDIT swapped, built through the same
 * JournalBuilder and validated by the same balance invariant as any other
 * posting. The only thing that makes it a "reversal" rather than a
 * coincidence is the idempotency key -- deterministic (REV:{id}), so
 * reversing the same transaction twice returns the first reversal instead
 * of creating a second one -- and reversalOfTransactionId, carried on the
 * new row.
 *
 * Deliberately not @Transactional. Wrapping this in its own transaction and
 * then calling PostingService.post() from inside it is exactly the trap the
 * project's own plan warns document services about: PostingExecutor is
 * REQUIRED, so a failure inside it would mark this method's transaction
 * rollback-only and silently fail every retry. There is nothing here that
 * needs a shared transaction anyway -- posting the reversal is the only
 * write, and "has the original been reversed" is answered by looking for
 * this row afterwards, not by writing back onto the original.
 */
@Service
public class ReversalService {

    private final TransactionService transactionService;
    private final PostingService postingService;

    public ReversalService(TransactionService transactionService, PostingService postingService) {
        this.transactionService = transactionService;
        this.postingService = postingService;
    }

    /**
     * @param reversalDate defaults to today if null -- reversing is
     *        routinely done on a different day than the mistake it corrects.
     * @param reason appended to the reversal's description; optional.
     */
    public Transaction reverse(Long originalTransactionId, LocalDate reversalDate, String reason) {
        Transaction original = transactionService.getTransaction(originalTransactionId);
        var originalEntries = transactionService.getEntries(originalTransactionId);

        LocalDate date = reversalDate != null ? reversalDate : LocalDate.now(ZoneOffset.UTC);
        String description = reason == null || reason.isBlank()
                ? "Reversal of TXN-%d".formatted(original.getId())
                : "Reversal of TXN-%d: %s".formatted(original.getId(), reason);

        JournalBuilder journal = JournalBuilder.forDate(date)
                .withIdempotencyKey("REV:" + original.getId())
                .describedAs(description)
                .reversing(original.getId());

        for (Entry entry : originalEntries) {
            Money amount = Money.of(entry.getAmount(), entry.getCurrency());
            Long accountId = entry.getAccount().getId();
            if (entry.getEntryType() == EntryType.DEBIT) {
                journal.credit(accountId, amount);
            } else {
                journal.debit(accountId, amount);
            }
        }

        return postingService.post(journal.build());
    }
}
