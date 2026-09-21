package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.exception.UnbalancedTransactionException;
import com.ledgerflow.money.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The balance invariant used to live in PostingService, which meant it was
 * checked once a command had already been built, passed around and handed to
 * a service. It now lives in PostingCommand's constructor, so these tests are
 * really asking one question: is an unbalanced journal entry constructible at
 * all? For a single-currency journal, still exactly that -- for the one
 * mixed-currency case milestone 15 introduces, see PostingCommand's own
 * Javadoc on where that check moved instead, and why.
 */
class JournalBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);

    @Test
    void buildsABalancedEntry() {
        PostingCommand command = JournalBuilder.forDate(TODAY)
                .withIdempotencyKey("INV:1:42:ISSUE")
                .describedAs("Invoice 1001")
                .debit(1L, Money.of("250.00", "USD"))
                .credit(3L, Money.of("250.00", "USD"))
                .build();

        assertThat(command.txnDate()).isEqualTo(TODAY);
        assertThat(command.currency()).isEqualTo("USD");
        assertThat(command.entries())
                .extracting(EntryLine::accountId, EntryLine::entryType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, EntryType.DEBIT),
                        org.assertj.core.groups.Tuple.tuple(3L, EntryType.CREDIT));
    }

    @Test
    void splitsAcrossSeveralLinesAsLongAsTheySum() {
        PostingCommand command = JournalBuilder.forDate(TODAY)
                .withIdempotencyKey("split-1")
                .debit(1L, Money.of("100.00", "USD"))
                .credit(2L, Money.of("60.00", "USD"))
                .credit(3L, Money.of("40.00", "USD"))
                .build();

        assertThat(command.entries()).hasSize(3);
    }

    @Test
    void refusesWhenDebitsDoNotEqualCredits() {
        assertThatThrownBy(() -> JournalBuilder.forDate(TODAY)
                        .withIdempotencyKey("bad-1")
                        .debit(1L, Money.of("100.00", "USD"))
                        .credit(2L, Money.of("50.00", "USD"))
                        .build())
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("100.00 USD")
                .hasMessageContaining("50.00 USD");
    }

    @Test
    void refusesASingleSidedEntry() {
        assertThatThrownBy(() -> JournalBuilder.forDate(TODAY)
                        .withIdempotencyKey("bad-2")
                        .debit(1L, Money.of("100.00", "USD"))
                        .build())
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("at least two lines");
    }

    @Test
    void refusesZeroAndNegativeAmounts() {
        // Zero balances trivially, which is exactly why it needs its own rule.
        assertThatThrownBy(() -> JournalBuilder.forDate(TODAY)
                        .withIdempotencyKey("bad-3")
                        .debit(1L, Money.zero("USD"))
                        .credit(2L, Money.zero("USD"))
                        .build())
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("greater than zero");

        // So does a negative pair -- and it would make "credit -50" and
        // "debit 50" two spellings of the same entry.
        assertThatThrownBy(() -> JournalBuilder.forDate(TODAY)
                        .withIdempotencyKey("bad-4")
                        .debit(1L, Money.of("-50.00", "USD"))
                        .credit(2L, Money.of("-50.00", "USD"))
                        .build())
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void aMixedCurrencyEntryIsNoLongerRejectedAtConstructionTime() {
        // Milestone 15: a genuine foreign-currency settlement mixes the
        // document's own currency with a base-currency-only FX adjustment
        // leg, and there is no way to know here whether 100 USD of debits
        // against 100 EUR of credits balances -- that needs an exchange
        // rate, which this record has no access to. See PostingCommand's
        // own Javadoc: building one no longer throws, and PostingExecutor
        // is where a mixed-currency command is actually checked instead.
        PostingCommand command = JournalBuilder.forDate(TODAY)
                .withIdempotencyKey("mixed-1")
                .debit(1L, Money.of("100.00", "USD"))
                .credit(2L, Money.of("100.00", "EUR"))
                .build();

        assertThat(command.entries()).hasSize(2);
    }

    @Test
    void refusesAnEntryWithNothingToDeduplicateOn() {
        assertThatThrownBy(() -> JournalBuilder.forDate(TODAY)
                        .debit(1L, Money.of("1.00", "USD"))
                        .credit(2L, Money.of("1.00", "USD"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key");
    }

    @Test
    void theInvariantCannotBeBypassedByConstructingTheCommandDirectly() {
        // The builder is ergonomics. The rule lives in the value itself, so
        // there is no back door for a future caller that skips the builder.
        assertThatThrownBy(() -> new PostingCommand(
                        "direct-1",
                        "constructed by hand",
                        TODAY,
                        List.of(
                                new EntryLine(1L, EntryType.DEBIT, Money.of("100.00", "USD")),
                                new EntryLine(2L, EntryType.CREDIT, Money.of("1.00", "USD")))))
                .isInstanceOf(UnbalancedTransactionException.class);
    }

    @Test
    void entriesAreDefensivelyCopied() {
        java.util.List<EntryLine> mutable = new java.util.ArrayList<>(List.of(
                new EntryLine(1L, EntryType.DEBIT, Money.of("10.00", "USD")),
                new EntryLine(2L, EntryType.CREDIT, Money.of("10.00", "USD"))));

        PostingCommand command = new PostingCommand("copy-1", null, TODAY, mutable);
        mutable.add(new EntryLine(3L, EntryType.DEBIT, Money.of("999.00", "USD")));

        // Otherwise a validated command could be made unbalanced after the
        // fact, which would defeat the whole arrangement.
        assertThat(command.entries()).hasSize(2);
    }
}
