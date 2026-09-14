package com.ledgerflow.it;

import static java.util.Comparator.reverseOrder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.AccountWithBalance;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.BalanceSnapshotService;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.LedgerEntry;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Balances are no longer stored. These tests pin down what that buys and what
 * it must not cost.
 */
class DerivedBalanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PostingService postingService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BalanceSnapshotService snapshotService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountBalanceQueries accountBalanceQueries;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void thereIsNoStoredBalanceColumnLeftToDriftFromTheEntries() {
        // The strongest guarantee available: the number cannot disagree with
        // the entries because there is nowhere else for it to live.
        assertThat(owner.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'accounts' AND column_name = 'balance'
                        """,
                        Long.class))
                .isZero();
    }

    @Test
    void aBalanceIsTheSumOfItsEntries() {
        Account cash = newAccount("Derived Cash");
        Account revenue = newAccount("Derived Revenue");

        post(cash, revenue, "100.00", LocalDate.of(2026, 1, 10));
        post(cash, revenue, "40.00", LocalDate.of(2026, 1, 20));
        post(revenue, cash, "25.00", LocalDate.of(2026, 2, 5));

        assertThat(balanceService.getBalance(cash.getId()).balance()).isEqualByComparingTo("115.00");
        assertThat(balanceService.getBalance(revenue.getId()).balance()).isEqualByComparingTo("-115.00");

        // Every account nets to zero across the ledger, which is the whole
        // point of double entry and is now structurally true rather than
        // maintained.
        assertThat(balanceService
                        .getBalance(cash.getId())
                        .balance()
                        .add(balanceService.getBalance(revenue.getId()).balance()))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void aSnapshotChangesHowTheBalanceIsComputedButNotWhatItIs() {
        Account cash = newAccount("Snapshot Cash");
        Account revenue = newAccount("Snapshot Revenue");

        post(cash, revenue, "60.00", LocalDate.of(2026, 1, 15));
        post(cash, revenue, "30.00", LocalDate.of(2026, 2, 15));

        BigDecimal beforeSnapshot = balanceService.getBalance(cash.getId()).balance();
        assertThat(beforeSnapshot).isEqualByComparingTo("90.00");

        // Checkpoint at the end of January: 60.00 is now read from the
        // snapshot and only February's 30.00 is summed from entries.
        snapshotService.snapshotAsOf(LocalDate.of(2026, 1, 31));

        assertThat(owner.queryForObject(
                        "SELECT balance FROM account_balance_snapshots WHERE account_id = ? AND as_of_date = ?",
                        BigDecimal.class,
                        cash.getId(),
                        LocalDate.of(2026, 1, 31)))
                .isEqualByComparingTo("60.00");

        assertThat(balanceService.getBalance(cash.getId()).balance())
                .as("a snapshot is an optimization; it must not change the answer")
                .isEqualByComparingTo(beforeSnapshot);

        // And deleting it must not change the answer either -- the snapshot
        // is never the source of truth.
        owner.update("DELETE FROM account_balance_snapshots WHERE account_id = ?", cash.getId());
        assertThat(balanceService.getBalance(cash.getId()).balance()).isEqualByComparingTo(beforeSnapshot);
    }

    @Test
    void aBackDatedEntryLandsInAccountingOrderNotInsertOrder() {
        Account cash = newAccount("Backdated Cash");
        Account revenue = newAccount("Backdated Revenue");

        post(cash, revenue, "100.00", LocalDate.of(2026, 3, 31));
        // Entered later, effective earlier -- a correction filed after the fact.
        post(cash, revenue, "10.00", LocalDate.of(2026, 3, 1));

        List<LedgerEntry> statement = balanceService.getLedgerEntries(cash.getId());

        // Newest first, by accounting date: the March 31 entry leads even
        // though it was written first.
        assertThat(statement).extracting(LedgerEntry::txnDate)
                .containsExactly(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1));

        // The running balances are folded in accounting order, so the
        // correction is accounted for *before* the later entry, not tacked on
        // after it.
        assertThat(statement.get(1).runningBalance()).isEqualByComparingTo("10.00");
        assertThat(statement.get(0).runningBalance()).isEqualByComparingTo("110.00");

        // The fold and the SQL sum are two computations of one number.
        assertThat(statement.get(0).runningBalance())
                .isEqualByComparingTo(balanceService.getBalance(cash.getId()).balance());
    }

    @Test
    void concurrentPostingsToOneAccountAllSucceed() throws Exception {
        // This is the milestone. With a stored balance guarded by an
        // optimistic lock, twelve postings racing on one account exhausted
        // PostingService's three retries and threw -- and in a real business
        // every invoice, payment and bill lands on the same few accounts, so
        // this is ordinary use rather than a stress test. Appending entries
        // is insert-only, so there is nothing left to contend on.
        Account cash = newAccount("Contended Cash");
        Account revenue = newAccount("Contended Revenue");

        int postings = 12;
        ExecutorService pool = Executors.newFixedThreadPool(postings);
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            List<Future<Transaction>> futures = IntStream.range(0, postings)
                    .mapToObj(i -> pool.submit(() -> {
                        startTogether.await();
                        // Each thread is its own request as far as tenancy is
                        // concerned, and a ThreadLocal does not follow it here.
                        return TenantContext.runAs(DEMO_ORG_ID, () -> postingService.post(JournalBuilder.forDate(
                                        LocalDate.of(2026, 4, 1))
                                .withIdempotencyKey("contended-" + UUID.randomUUID())
                                .describedAs("concurrent posting " + i)
                                .debit(cash.getId(), Money.of("10.00", "USD"))
                                .credit(revenue.getId(), Money.of("10.00", "USD"))
                                .build()));
                    }))
                    .toList();

            startTogether.countDown();
            for (Future<Transaction> future : futures) {
                // Fails the test with the cause if any posting threw.
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(balanceService.getBalance(cash.getId()).balance()).isEqualByComparingTo("120.00");
        assertThat(balanceService.getBalance(revenue.getId()).balance()).isEqualByComparingTo("-120.00");
        assertThat(balanceService.getLedgerEntries(cash.getId())).hasSize(postings);
    }

    @Test
    void theAccountListSortsByABalanceThatIsNotAColumn() {
        // Sorting has to happen inside the query. A value computed after the
        // page has been chosen cannot order that page -- you would be sorting
        // whichever twenty-five rows happened to come back.
        Account small = newAccount("ZZZ Sort Small");
        Account large = newAccount("AAA Sort Large");
        Account counterparty = newAccount("Sort Counterparty");

        post(small, counterparty, "5.00", LocalDate.of(2026, 5, 1));
        post(large, counterparty, "5000.00", LocalDate.of(2026, 5, 1));

        List<AccountWithBalance> descending = accountBalanceQueries
                .search("Sort ", null, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "balance")))
                .getContent();

        // Names sort the other way round, so this could not pass by accident.
        assertThat(descending).extracting(AccountWithBalance::id).startsWith(large.getId());
        assertThat(descending).extracting(AccountWithBalance::balance).isSortedAccordingTo(reverseOrder());

        List<AccountWithBalance> ascending = accountBalanceQueries
                .search("Sort ", null, PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "balance")))
                .getContent();
        assertThat(ascending).extracting(AccountWithBalance::balance).isSorted();

        // A sort property outside the whitelist must not reach the SQL.
        List<AccountWithBalance> unknownSort = accountBalanceQueries
                .search("Sort ", null, PageRequest.of(0, 50, Sort.by("; DROP TABLE accounts--")))
                .getContent();
        assertThat(unknownSort).extracting(AccountWithBalance::name).isSorted();
        assertThat(owner.queryForObject("SELECT count(*) FROM accounts", Long.class)).isPositive();
    }

    private void post(Account debit, Account credit, String amount, LocalDate date) {
        postingService.post(JournalBuilder.forDate(date)
                .withIdempotencyKey("derived-" + UUID.randomUUID())
                .describedAs("derived balance test")
                .debit(debit.getId(), Money.of(amount, "USD"))
                .credit(credit.getId(), Money.of(amount, "USD"))
                .build());
    }

    private Account newAccount(String name) {
        Account account = new Account();
        account.setName(name + " " + UUID.randomUUID());
        account.setCurrency("USD");
        account.setOrgId(DEMO_ORG_ID);
        account.setType(AccountType.ASSET);
        return accountRepository.save(account);
    }
}
