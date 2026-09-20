package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.AccountingPeriod;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.PeriodException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.PeriodService;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.service.ReversalService;
import com.ledgerflow.service.TransactionService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Period locking and reversal, exercised against real Postgres.
 *
 * Two facts specifically need the real database to prove, not a mock:
 * whether the GIST exclusion constraint actually rejects an overlapping
 * period, and whether the BEFORE INSERT trigger actually rejects a posting
 * into a closed one once the application-level check is bypassed.
 */
class PeriodsAndReversalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PeriodService periodService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private ReversalService reversalService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void twoPeriodsForOneOrganizationCannotOverlap() {
        periodService.create(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThatThrownBy(() -> periodService.create(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30)))
                .isInstanceOf(PeriodException.class)
                .satisfies(e -> assertThat(((PeriodException) e).getCode()).isEqualTo("OVERLAPPING_PERIOD"));

        // Adjacent, not overlapping, is fine.
        assertThat(periodService.create(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30))).isNotNull();
    }

    @Test
    void closingAPeriodRejectsANewPostingDatedInsideItButNotOutsideIt() {
        Account debit = newAccount("Period Debit", AccountType.ASSET);
        Account credit = newAccount("Period Credit", AccountType.REVENUE);

        AccountingPeriod period = periodService.create(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        periodService.close(period.getId());

        assertThatThrownBy(() -> post(debit, credit, "10.00", LocalDate.of(2026, 7, 15)))
                .isInstanceOf(PeriodException.class)
                .satisfies(e -> assertThat(((PeriodException) e).getCode()).isEqualTo("PERIOD_CLOSED"));

        // The day right after the closed period is untouched by it.
        Transaction outside = post(debit, credit, "10.00", LocalDate.of(2026, 8, 1));
        assertThat(outside.getId()).isNotNull();
    }

    @Test
    void reopeningAClosedPeriodAllowsPostingIntoItAgain() {
        Account debit = newAccount("Reopen Debit", AccountType.ASSET);
        Account credit = newAccount("Reopen Credit", AccountType.REVENUE);

        AccountingPeriod period = periodService.create(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        periodService.close(period.getId());
        periodService.reopen(period.getId());

        Transaction posted = post(debit, credit, "5.00", LocalDate.of(2026, 9, 15));
        assertThat(posted.getId()).isNotNull();
    }

    @Test
    void closingAnAlreadyClosedPeriodIsRefusedRatherThanSilentlyRepeated() {
        AccountingPeriod period = periodService.create(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        periodService.close(period.getId());

        assertThatThrownBy(() -> periodService.close(period.getId()))
                .isInstanceOf(PeriodException.class)
                .satisfies(e -> assertThat(((PeriodException) e).getCode()).isEqualTo("ALREADY_CLOSED"));
    }

    /**
     * The application-level check in PeriodService.assertOpen is a courtesy;
     * this proves the actual defense -- the database trigger -- independent
     * of it, by writing around the application entirely as the owner.
     */
    @Test
    void theDatabaseItselfRejectsAPostingIntoAClosedPeriodEvenBypassingTheApplication() {
        AccountingPeriod period = periodService.create(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        periodService.close(period.getId());

        assertThatThrownBy(() -> owner.update(
                        "INSERT INTO transactions (org_id, idempotency_key, description, status, txn_date)"
                                + " VALUES (?, ?, ?, 'POSTED', ?)",
                        DEMO_ORG_ID,
                        "trigger-test-" + UUID.randomUUID(),
                        "should never land",
                        LocalDate.of(2026, 11, 15)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("closed accounting period");
    }

    @Test
    void reversingATransactionMirrorsEveryLineWithDirectionSwapped() {
        Account cash = newAccount("Reversal Cash", AccountType.ASSET);
        Account revenue = newAccount("Reversal Revenue", AccountType.REVENUE);

        Transaction original = post(cash, revenue, "40.00", LocalDate.of(2026, 5, 1));

        Transaction reversal = reversalService.reverse(original.getId(), LocalDate.of(2026, 5, 2), "mistake");

        assertThat(reversal.getId()).isNotEqualTo(original.getId());
        assertThat(reversal.getReversalOfTransactionId()).isEqualTo(original.getId());
        assertThat(reversal.getTxnDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(reversal.getDescription()).contains("TXN-" + original.getId()).contains("mistake");

        // Derived, not stored on the original -- proving the lookup finds it.
        assertThat(transactionService.reversedBy(original.getId())).contains(reversal.getId());
        assertThat(transactionService.reversedBy(reversal.getId())).isEmpty();

        var reversalEntries = transactionService.getEntries(reversal.getId());
        assertThat(reversalEntries).hasSize(2);
        assertThat(reversalEntries)
                .filteredOn(e -> e.getAccount().getId().equals(cash.getId()))
                .singleElement()
                .satisfies(e -> assertThat(e.getEntryType().name()).isEqualTo("CREDIT"));
        assertThat(reversalEntries)
                .filteredOn(e -> e.getAccount().getId().equals(revenue.getId()))
                .singleElement()
                .satisfies(e -> assertThat(e.getEntryType().name()).isEqualTo("DEBIT"));
    }

    @Test
    void reversingTheSameTransactionTwiceReturnsTheFirstReversal() {
        Account cash = newAccount("Idempotent Reversal Cash", AccountType.ASSET);
        Account revenue = newAccount("Idempotent Reversal Revenue", AccountType.REVENUE);
        Transaction original = post(cash, revenue, "15.00", LocalDate.of(2026, 5, 3));

        Transaction first = reversalService.reverse(original.getId(), LocalDate.of(2026, 5, 4), null);
        Transaction second = reversalService.reverse(original.getId(), LocalDate.of(2026, 5, 4), null);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(owner.queryForObject(
                        "SELECT count(*) FROM transactions WHERE reversal_of_transaction_id = ?",
                        Long.class,
                        original.getId()))
                .isEqualTo(1);
    }

    @Test
    void atMostOneDirectReversalPerTransactionIsEnforcedByTheDatabase() {
        Account cash = newAccount("Unique Reversal Cash", AccountType.ASSET);
        Account revenue = newAccount("Unique Reversal Revenue", AccountType.REVENUE);
        Transaction original = post(cash, revenue, "20.00", LocalDate.of(2026, 5, 5));
        reversalService.reverse(original.getId(), LocalDate.of(2026, 5, 6), null);

        // A second, differently-keyed "reversal" of the same original --
        // not reachable through ReversalService, which always uses the
        // deterministic key, but the constraint must hold regardless of how
        // a row is written.
        assertThatThrownBy(() -> owner.update(
                        "INSERT INTO transactions"
                                + " (org_id, idempotency_key, description, status, txn_date, reversal_of_transaction_id)"
                                + " VALUES (?, ?, ?, 'POSTED', ?, ?)",
                        DEMO_ORG_ID,
                        "second-reversal-" + UUID.randomUUID(),
                        "a second correction",
                        LocalDate.of(2026, 5, 7),
                        original.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    private Transaction post(Account debit, Account credit, String amount, LocalDate date) {
        return postingService.post(JournalBuilder.forDate(date)
                .withIdempotencyKey("period-it-" + UUID.randomUUID())
                .describedAs("period/reversal test posting")
                .debit(debit.getId(), Money.of(amount, "USD"))
                .credit(credit.getId(), Money.of(amount, "USD"))
                .build());
    }

    private Account newAccount(String name, AccountType type) {
        Account account = new Account();
        account.setCode("T" + UUID.randomUUID().toString().substring(0, 8));
        account.setName(name + " " + UUID.randomUUID());
        account.setCurrency("USD");
        account.setOrgId(DEMO_ORG_ID);
        account.setType(type);
        return accountRepository.save(account);
    }
}
