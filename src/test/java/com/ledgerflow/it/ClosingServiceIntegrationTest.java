package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.ChartOfAccountsException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.ClosingService;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The year-end closing journal, exercised against real Postgres so the
 * balance-as-of-date query and the plug-line arithmetic are proven together,
 * not asserted separately and trusted to compose correctly.
 */
class ClosingServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ClosingService closingService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private AccountRepository accountRepository;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void closingZeroesRevenueAndExpenseAndMovesTheNetToRetainedEarnings() {
        Account cash = newAccount("Close Cash", AccountType.ASSET);
        Account revenue = newAccount("Close Revenue", AccountType.REVENUE);
        Account expense = newAccount("Close Expense", AccountType.EXPENSE);
        LocalDate closingDate = LocalDate.of(2020, 12, 31);

        // 100 of revenue, 30 of expense: net income 70.
        post(cash, revenue, "100.00", LocalDate.of(2020, 6, 1));
        post(expense, cash, "30.00", LocalDate.of(2020, 6, 2));

        assertThat(balanceService.getBalance(revenue.getId()).balance()).isEqualByComparingTo("-100.00");
        assertThat(balanceService.getBalance(expense.getId()).balance()).isEqualByComparingTo("30.00");

        Transaction closingJournal = closingService.closeFiscalYear(closingDate);

        assertThat(closingJournal.getTxnDate()).isEqualTo(closingDate);
        assertThat(balanceService.getBalance(revenue.getId()).balance())
                .as("closing brings every revenue account to exactly zero")
                .isEqualByComparingTo("0.00");
        assertThat(balanceService.getBalance(expense.getId()).balance())
                .as("and every expense account too")
                .isEqualByComparingTo("0.00");

        Account retainedEarnings = accountRepository
                .findBySystemRole(SystemAccountRole.RETAINED_EARNINGS)
                .orElseThrow();
        // Net income of 70 (100 revenue - 30 expense) lands as a credit,
        // which is negative in this ledger's uniform DEBIT-adds/CREDIT-
        // subtracts convention -- the same convention that already shows
        // revenue itself as -100.00 above.
        assertThat(balanceService.getBalance(retainedEarnings.getId()).balance())
                .isEqualByComparingTo("-70.00");

        // The ledger as a whole still nets to zero -- closing moved value
        // between accounts, it did not create or destroy any. base_amount,
        // not amount: a foreign-currency settlement elsewhere in this same
        // org (see FxIntegrationTest) genuinely mixes currencies within one
        // transaction, balanced only in the reporting currency, so summing
        // raw amount across every entry in the org adds euros to dollars.
        assertThat(owner.queryForObject(
                        "SELECT COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN base_amount ELSE -base_amount END), 0)"
                                + " FROM entries WHERE org_id = ?",
                        BigDecimal.class,
                        DEMO_ORG_ID))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void closingTheSameYearTwiceDoesNotDoubleCountIncome() {
        Account cash = newAccount("Idempotent Close Cash", AccountType.ASSET);
        Account revenue = newAccount("Idempotent Close Revenue", AccountType.REVENUE);
        LocalDate closingDate = LocalDate.of(2021, 12, 31);
        post(cash, revenue, "50.00", LocalDate.of(2021, 3, 1));

        Transaction first = closingService.closeFiscalYear(closingDate);
        Transaction second = closingService.closeFiscalYear(closingDate);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(balanceService.getBalance(revenue.getId()).balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void closingAYearWithNothingToCloseIsRefusedRatherThanPostingAnEmptyJournal() {
        // A date with no revenue or expense activity of its own -- 2019 has
        // not been touched by any other test in this class.
        assertThatThrownBy(() -> closingService.closeFiscalYear(LocalDate.of(2019, 12, 31)))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("NOTHING_TO_CLOSE"));
    }

    @Test
    void anOrganizationWithNoRetainedEarningsAccountCannotCloseTheYear() {
        Long bareOrgId = owner.queryForObject(
                "INSERT INTO organizations (name, base_currency) VALUES (?, 'USD') RETURNING id",
                Long.class,
                "Closing Test Bare Org " + UUID.randomUUID());

        TenantContext.runAs(bareOrgId, () -> {
            assertThatThrownBy(() -> closingService.closeFiscalYear(LocalDate.of(2027, 12, 31)))
                    .isInstanceOf(ChartOfAccountsException.class)
                    .satisfies(e ->
                            assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("MISSING_SYSTEM_ACCOUNT"));
            return null;
        });
    }

    private void post(Account debit, Account credit, String amount, LocalDate date) {
        postingService.post(JournalBuilder.forDate(date)
                .withIdempotencyKey("closing-it-" + UUID.randomUUID())
                .describedAs("closing test posting")
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
