package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the row-level security policies actually isolate organizations.
 *
 * Seeding runs through a second DataSource on the owning role, because the
 * whole point of the policies is that the application identity cannot read or
 * write another organization's rows -- so org B's fixtures cannot be created
 * through the application's own connection.
 *
 * Deliberately not @Transactional. Spring begins a transactional test's
 * transaction in beforeTestMethod, which runs *before* @BeforeEach -- so the
 * tenant would be established after the transaction had already started, and
 * the GUC that the policies read is set as a transaction begins. Letting each
 * repository call open its own transaction also matches how the application
 * actually runs.
 *
 * Note what a cross-tenant lookup returns: empty, not denied. RLS filters
 * rows out of the result set rather than raising, so application code sees
 * "not found" and cannot tell a row that never existed from one belonging to
 * someone else. An error would itself confirm the row exists.
 */
class RlsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    private JdbcTemplate owner;
    private Long otherOrgId;
    private Long otherOrgAccountId;

    @BeforeEach
    void seedASecondOrganization() {
        owner = ownerJdbc();

        otherOrgId = owner.queryForObject(
                "INSERT INTO organizations (name, base_currency) VALUES (?, 'USD') RETURNING id",
                Long.class,
                "Other Co " + UUID.randomUUID());

        otherOrgAccountId = owner.queryForObject(
                """
                INSERT INTO accounts (org_id, code, name, currency, type, version, created_at, updated_at)
                VALUES (?, ?, ?, 'USD', 'ASSET', 0, now(), now())
                RETURNING id
                """,
                Long.class,
                otherOrgId,
                "T" + UUID.randomUUID().toString().substring(0, 8),
                "Other Co Cash " + UUID.randomUUID());
    }

    @Test
    void aListQueryOnlyEverSeesTheCurrentOrganizationsRows() {
        Account mine = new Account();
        mine.setCode("T" + UUID.randomUUID().toString().substring(0, 8));
        mine.setOrgId(DEMO_ORG_ID);
        mine.setName("RLS Demo Account " + UUID.randomUUID());
        mine.setCurrency("USD");
        mine.setType(AccountType.ASSET);
        accountRepository.save(mine);

        assertThat(accountRepository.findAll())
                .isNotEmpty()
                .allMatch(account -> DEMO_ORG_ID.equals(account.getOrgId()));
    }

    @Test
    void anotherOrganizationsRowIsInvisibleEvenWhenItsIdIsKnown() {
        // The row demonstrably exists -- the owner connection can see it.
        assertThat(owner.queryForObject("SELECT count(*) FROM accounts WHERE id = ?", Long.class, otherOrgAccountId))
                .isEqualTo(1);

        assertThat(accountRepository.findById(otherOrgAccountId)).isEmpty();
    }

    @Test
    void switchingTenantSwitchesWhatIsVisible() {
        assertThat(accountRepository.findById(otherOrgAccountId)).isEmpty();

        TenantContext.runAs(
                otherOrgId, () -> assertThat(accountRepository.findById(otherOrgAccountId)).isPresent());

        // And back again, proving the context did not leak.
        assertThat(accountRepository.findById(otherOrgAccountId)).isEmpty();
    }

    @Test
    void withNoTenantSetTheApplicationSeesNothingAtAll() {
        TenantContext.clear();

        // Fail closed: current_setting(..., true) yields NULL, org_id = NULL
        // is never true, so every row is filtered. The dangerous alternative
        // -- a policy that lets an unset context through -- would hand every
        // organization's data to a request that forgot to scope itself.
        assertThat(accountRepository.findAll()).isEmpty();
    }
}
