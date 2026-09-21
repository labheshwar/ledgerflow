package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.exception.ChartOfAccountsException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.AccountDraft;
import com.ledgerflow.service.AccountNode;
import com.ledgerflow.service.ChartOfAccountsService;
import com.ledgerflow.service.ChartOfAccountsSeeder;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The rules of the chart of accounts, exercised against real Postgres.
 *
 * Two constraints in particular -- the per-organization uniqueness of code
 * and system role -- are enforced by unique indexes, not by application code
 * checking first. A mocked repository test cannot see either: it would have
 * to fake the exact constraint violation ChartOfAccountsService.save()
 * catches, which is precisely the thing worth proving actually happens.
 */
class ChartOfAccountsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChartOfAccountsService chartOfAccounts;

    @Autowired
    private ChartOfAccountsSeeder seeder;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PostingService postingService;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void createResolvesDefaultsWhenTheCallerOmitsThem() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));

        // No currency was supplied, so it falls back to the organization's
        // reporting currency rather than leaving the account unusable.
        assertThat(account.getCurrency()).isEqualTo("USD");
        assertThat(account.isPostable()).isTrue();
        assertThat(account.getOrgId()).isEqualTo(DEMO_ORG_ID);
    }

    @Test
    void codeIsUniquePerOrganizationButNotGlobally() {
        String code = uniqueCode();
        chartOfAccounts.create(draft(code, AccountType.ASSET));

        assertThatThrownBy(() -> chartOfAccounts.create(draft(code, AccountType.ASSET)))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("DUPLICATE_CODE"));

        // The same code in a different organization is not a collision --
        // two businesses both numbering their cash account 1000 is normal.
        Long otherOrgId = createBareOrganization();
        Account inOtherOrg = TenantContext.runAs(otherOrgId, () -> {
            return chartOfAccounts.create(draft(code, AccountType.ASSET));
        });
        assertThat(inOtherOrg.getCode()).isEqualTo(code);
    }

    @Test
    void aChildMustShareItsParentsType() {
        Account assetHeading = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));

        assertThatThrownBy(() -> chartOfAccounts.create(
                        draftUnder(uniqueCode(), AccountType.LIABILITY, assetHeading.getId())))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("INVALID_PARENT"));
    }

    @Test
    void aParentWithEntriesOfItsOwnCannotAdoptAChild() {
        Account posted = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));
        Account counterparty = chartOfAccounts.create(draft(uniqueCode(), AccountType.REVENUE));
        post(posted, counterparty, "10.00");

        assertThatThrownBy(() -> chartOfAccounts.create(draftUnder(uniqueCode(), AccountType.ASSET, posted.getId())))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("INVALID_PARENT"));
    }

    @Test
    void givingAnAccountAChildTurnsItIntoAHeadingAutomatically() {
        Account parent = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));
        assertThat(parent.isPostable()).isTrue();

        chartOfAccounts.create(draftUnder(uniqueCode(), AccountType.EXPENSE, parent.getId()));

        Account reloaded = accountRepository.findById(parent.getId()).orElseThrow();
        assertThat(reloaded.isPostable())
                .as("a heading's balance is the sum of its children; it cannot also hold entries directly")
                .isFalse();
    }

    @Test
    void aParentCannotBeMovedUnderItsOwnDescendant() {
        Account grandparent = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));
        Account parent = chartOfAccounts.create(draftUnder(uniqueCode(), AccountType.EXPENSE, grandparent.getId()));

        assertThatThrownBy(() -> chartOfAccounts.update(
                        grandparent.getId(), draftUnder(grandparent.getCode(), AccountType.EXPENSE, parent.getId())))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("INVALID_PARENT"));
    }

    @Test
    void anAccountWithEntriesCannotBeRetyped() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));
        Account counterparty = chartOfAccounts.create(draft(uniqueCode(), AccountType.REVENUE));
        post(account, counterparty, "10.00");

        assertThatThrownBy(() -> chartOfAccounts.update(
                        account.getId(), draft(account.getCode(), AccountType.LIABILITY)))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("ACCOUNT_IN_USE"));
    }

    @Test
    void anAccountWithEntriesCannotChangeCurrency() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));
        Account counterparty = chartOfAccounts.create(draft(uniqueCode(), AccountType.REVENUE));
        post(account, counterparty, "10.00");

        AccountDraft eurDraft = new AccountDraft(
                account.getCode(), account.getName(), null, AccountType.ASSET, "EUR", null, null, true);

        assertThatThrownBy(() -> chartOfAccounts.update(account.getId(), eurDraft))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("ACCOUNT_IN_USE"));
    }

    @Test
    void anAccountWithEntriesCannotBeMadeAHeading() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));
        Account counterparty = chartOfAccounts.create(draft(uniqueCode(), AccountType.REVENUE));
        post(account, counterparty, "10.00");

        AccountDraft asHeading = new AccountDraft(
                account.getCode(), account.getName(), null, AccountType.ASSET, null, null, null, false);

        assertThatThrownBy(() -> chartOfAccounts.update(account.getId(), asHeading))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("ACCOUNT_IN_USE"));
    }

    @Test
    void aSystemAccountCannotBeArchived() {
        // Seeded by V14: the demo organization's Cash account is CASH.
        Account cash = accountRepository.findBySystemRole(SystemAccountRole.CASH).orElseThrow();

        assertThatThrownBy(() -> chartOfAccounts.archive(cash.getId()))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("SYSTEM_ACCOUNT"));
    }

    @Test
    void anAccountWithChildrenCannotBeArchivedOrDeleted() {
        Account parent = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));
        chartOfAccounts.create(draftUnder(uniqueCode(), AccountType.EXPENSE, parent.getId()));

        assertThatThrownBy(() -> chartOfAccounts.archive(parent.getId()))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("HAS_CHILDREN"));

        assertThatThrownBy(() -> chartOfAccounts.delete(parent.getId()))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("HAS_CHILDREN"));
    }

    @Test
    void archiveAndRestoreRoundTrip() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));

        Account archived = chartOfAccounts.archive(account.getId());
        assertThat(archived.isArchived()).isTrue();
        assertThat(chartOfAccounts.chart(false).stream().flatMap(node -> node.flatten().stream()))
                .as("archived accounts are hidden from the working chart by default")
                .noneMatch(node -> node.account().id().equals(account.getId()));

        Account restored = chartOfAccounts.restore(account.getId());
        assertThat(restored.isArchived()).isFalse();
        assertThat(chartOfAccounts.chart(false).stream().flatMap(node -> node.flatten().stream()))
                .anyMatch(node -> node.account().id().equals(account.getId()));
    }

    @Test
    void anAccountWithEntriesCanOnlyBeArchivedNeverDeleted() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));
        Account counterparty = chartOfAccounts.create(draft(uniqueCode(), AccountType.REVENUE));
        post(account, counterparty, "10.00");

        assertThatThrownBy(() -> chartOfAccounts.delete(account.getId()))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("ACCOUNT_IN_USE"));

        // Archiving the same account succeeds -- the entries are exactly why
        // deletion is refused, and exactly why archiving is not.
        assertThat(chartOfAccounts.archive(account.getId()).isArchived()).isTrue();
    }

    @Test
    void aFreshAccountWithNoEntriesCanBeDeletedOutright() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));

        chartOfAccounts.delete(account.getId());

        assertThat(accountRepository.findById(account.getId())).isEmpty();
    }

    @Test
    void requireByRoleFindsTheAccountTheApplicationMeansAndFailsClearlyWhenMissing() {
        Account cash = chartOfAccounts.requireByRole(SystemAccountRole.CASH);
        assertThat(cash.getSystemRole()).isEqualTo(SystemAccountRole.CASH);

        // Nothing in the demo organization is assigned this role -- unlike
        // FX_GAIN_LOSS, ROUNDING has never been backfilled onto it, since
        // nothing has posted to that role yet either.
        assertThatThrownBy(() -> chartOfAccounts.requireByRole(SystemAccountRole.ROUNDING))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode())
                        .isEqualTo("MISSING_SYSTEM_ACCOUNT"));
    }

    @Test
    void theChartRollsBalancesUpIntoTheirHeadings() {
        Account heading = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));
        Account childA = chartOfAccounts.create(draftUnder(uniqueCode(), AccountType.EXPENSE, heading.getId()));
        Account childB = chartOfAccounts.create(draftUnder(uniqueCode(), AccountType.EXPENSE, heading.getId()));
        Account cashSource = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));

        // Two debits to the expense children, both credited from the same
        // asset account, so the heading's rollup is unambiguous to compute
        // by hand: 30.00 + 12.50.
        post(childA, cashSource, "30.00");
        post(childB, cashSource, "12.50");

        AccountNode headingNode = chartOfAccounts.chart(false).stream()
                .flatMap(node -> node.flatten().stream())
                .filter(node -> node.account().id().equals(heading.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(headingNode.account().rollupBalance()).isEqualByComparingTo("42.50");
        assertThat(headingNode.children()).hasSize(2);

        // A leaf's rollup is just its own balance -- there is nothing beneath it.
        AccountNode childANode = headingNode.children().stream()
                .filter(node -> node.account().id().equals(childA.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(childANode.account().rollupBalance()).isEqualByComparingTo("30.00");
    }

    @Test
    void postingToAHeadingAccountIsRefused() {
        Account heading = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));
        chartOfAccounts.create(draftUnder(uniqueCode(), AccountType.EXPENSE, heading.getId()));
        Account counterparty = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));

        assertThatThrownBy(() -> post(heading, counterparty, "1.00"))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode())
                        .isEqualTo("ACCOUNT_NOT_POSTABLE"));
    }

    @Test
    void postingToAnArchivedAccountIsRefused() {
        Account account = chartOfAccounts.create(draft(uniqueCode(), AccountType.EXPENSE));
        Account counterparty = chartOfAccounts.create(draft(uniqueCode(), AccountType.ASSET));
        chartOfAccounts.archive(account.getId());

        assertThatThrownBy(() -> post(account, counterparty, "1.00"))
                .isInstanceOf(ChartOfAccountsException.class)
                .satisfies(e -> assertThat(((ChartOfAccountsException) e).getCode()).isEqualTo("ACCOUNT_ARCHIVED"));
    }

    @Test
    void theSeederPopulatesANewOrganizationsChartExactlyOnce() {
        Long newOrgId = createBareOrganization();

        int seeded = TenantContext.runAs(newOrgId, () -> {
            return seeder.seedDefaultChart();
        });
        assertThat(seeded).isPositive();

        List<AccountNode> chart = TenantContext.runAs(newOrgId, () -> {
            return chartOfAccounts.chart(true);
        });
        assertThat(chart.stream().flatMap(node -> node.flatten().stream()).map(node -> node.account().code()))
                .contains("1010", "1100", "2000", "3000", "4000");

        // Every system role the seed data promises actually resolves.
        TenantContext.runAs(newOrgId, (Runnable) () -> {
            assertThat(chartOfAccounts.requireByRole(SystemAccountRole.CASH)).isNotNull();
            assertThat(chartOfAccounts.requireByRole(SystemAccountRole.ACCOUNTS_RECEIVABLE)).isNotNull();
            assertThat(chartOfAccounts.requireByRole(SystemAccountRole.ACCOUNTS_PAYABLE)).isNotNull();
        });

        // Re-running must not duplicate a chart that already exists.
        int secondRun = TenantContext.runAs(newOrgId, () -> {
            return seeder.seedDefaultChart();
        });
        assertThat(secondRun).isZero();
    }

    private void post(Account debit, Account credit, String amount) {
        postingService.post(JournalBuilder.forDate(LocalDate.now())
                .withIdempotencyKey("coa-it-" + UUID.randomUUID())
                .describedAs("chart of accounts test posting")
                .debit(debit.getId(), Money.of(amount, "USD"))
                .credit(credit.getId(), Money.of(amount, "USD"))
                .build());
    }

    private AccountDraft draft(String code, AccountType type) {
        return new AccountDraft(code, "COA Test " + code, null, type, null, null, null, true);
    }

    private AccountDraft draftUnder(String code, AccountType type, Long parentId) {
        return new AccountDraft(code, "COA Test " + code, null, type, null, parentId, null, true);
    }

    private static String uniqueCode() {
        return "Z" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** A second organization with no accounts of its own, for cross-tenant checks. */
    private Long createBareOrganization() {
        return owner.queryForObject(
                "INSERT INTO organizations (name, base_currency) VALUES (?, 'USD') RETURNING id",
                Long.class,
                "COA Test Org " + UUID.randomUUID());
    }
}
