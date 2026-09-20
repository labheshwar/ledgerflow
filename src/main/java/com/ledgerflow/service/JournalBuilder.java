package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assembles a {@link PostingCommand} in something close to the language a
 * bookkeeper uses.
 *
 * This is convenience, not enforcement -- the balance invariant is checked by
 * PostingCommand's own constructor, so it holds whether or not a caller went
 * through here. What this adds is that the call site reads like the journal
 * it describes, which matters once invoices, bills, payments and FX
 * adjustments are all constructing entries:
 *
 * <pre>
 * JournalBuilder.forDate(invoice.issueDate())
 *     .describedAs("Invoice 1001")
 *     .withIdempotencyKey("INV:%d:%d:ISSUE".formatted(orgId, invoiceId))
 *     .debit(accountsReceivableId, Money.of("250.00", "USD"))
 *     .credit(revenueId, Money.of("250.00", "USD"))
 *     .build();
 * </pre>
 */
public final class JournalBuilder {

    private final LocalDate txnDate;
    private final List<EntryLine> lines = new ArrayList<>();
    private String description;
    private String idempotencyKey;
    private Long reversalOfTransactionId;

    private JournalBuilder(LocalDate txnDate) {
        this.txnDate = Objects.requireNonNull(txnDate, "txnDate");
    }

    public static JournalBuilder forDate(LocalDate txnDate) {
        return new JournalBuilder(txnDate);
    }

    public JournalBuilder describedAs(String description) {
        this.description = description;
        return this;
    }

    public JournalBuilder withIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    /** Marks this journal as undoing {@code originalTransactionId}. */
    public JournalBuilder reversing(Long originalTransactionId) {
        this.reversalOfTransactionId = Objects.requireNonNull(originalTransactionId, "originalTransactionId");
        return this;
    }

    public JournalBuilder debit(Long accountId, Money amount) {
        return line(accountId, EntryType.DEBIT, amount);
    }

    public JournalBuilder credit(Long accountId, Money amount) {
        return line(accountId, EntryType.CREDIT, amount);
    }

    private JournalBuilder line(Long accountId, EntryType type, Money amount) {
        lines.add(new EntryLine(Objects.requireNonNull(accountId, "accountId"), type, amount));
        return this;
    }

    /**
     * @throws com.ledgerflow.exception.UnbalancedTransactionException if the
     *         assembled entry does not satisfy double entry.
     */
    public PostingCommand build() {
        return new PostingCommand(idempotencyKey, description, txnDate, lines, reversalOfTransactionId);
    }
}
