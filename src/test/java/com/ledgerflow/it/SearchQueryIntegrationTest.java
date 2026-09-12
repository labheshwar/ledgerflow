package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.AuditLogRepository;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * These filters are all "optional" -- null means don't filter -- and that is
 * precisely where they break against a real database. Postgres cannot infer
 * the type of a null bind parameter that feeds LOWER(), and rejects the
 * statement with "function lower(bytea) does not exist". Nothing in a mocked
 * repository test can see that, so every optional filter combination gets
 * executed here against real Postgres.
 */
class SearchQueryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ReconciliationBatchRepository batchRepository;

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10, Sort.by("id"));

    @Test
    void accountSearchRunsWithEveryCombinationOfOptionalFilters() {
        String unique = "IT Search " + UUID.randomUUID();
        Account account = new Account();
        account.setName(unique);
        account.setCurrency("USD");
        account.setType(AccountType.ASSET);
        account.setBalance(BigDecimal.ZERO);
        accountRepository.save(account);

        // No filters at all -- the case that fails if a null reaches LOWER().
        assertThat(accountRepository.search(null, null, FIRST_PAGE).getTotalElements()).isPositive();

        // Type only, search term null.
        assertThat(accountRepository.search(null, AccountType.ASSET, FIRST_PAGE).getContent())
                .allMatch(a -> a.getType() == AccountType.ASSET);

        // Term only.
        assertThat(accountRepository.search(unique, null, FIRST_PAGE).getContent())
                .extracting(Account::getName)
                .containsExactly(unique);

        // Both together.
        assertThat(accountRepository.search(unique, AccountType.ASSET, FIRST_PAGE).getTotalElements())
                .isEqualTo(1);

        // A term matching nothing still has to execute cleanly.
        assertThat(accountRepository.search("no-such-account-" + UUID.randomUUID(), null, FIRST_PAGE))
                .isEmpty();
    }

    @Test
    void accountSearchIsCaseInsensitiveAndMatchesOnSubstrings() {
        String unique = "IT MixedCase " + UUID.randomUUID();
        Account account = new Account();
        account.setName(unique);
        account.setCurrency("USD");
        account.setType(AccountType.REVENUE);
        account.setBalance(BigDecimal.ZERO);
        accountRepository.save(account);

        assertThat(accountRepository.search("it mixedcase", null, FIRST_PAGE).getContent())
                .extracting(Account::getName)
                .contains(unique);
    }

    @Test
    void blankAndWhitespaceTermsBehaveLikeNoFilter() {
        long all = accountRepository.search(null, null, FIRST_PAGE).getTotalElements();

        assertThat(accountRepository.search("", null, FIRST_PAGE).getTotalElements()).isEqualTo(all);
        assertThat(accountRepository.search("   ", null, FIRST_PAGE).getTotalElements()).isEqualTo(all);
    }

    @Test
    void transactionSearchRunsWithAndWithoutATerm() {
        assertThat(transactionRepository.search(null, FIRST_PAGE)).isNotNull();
        assertThat(transactionRepository.search("", FIRST_PAGE)).isNotNull();
        assertThat(transactionRepository.search("no-such-transaction-" + UUID.randomUUID(), FIRST_PAGE))
                .isEmpty();
    }

    @Test
    void auditLogSearchRunsWithEveryCombinationOfOptionalFilters() {
        assertThat(auditLogRepository.search(null, null, FIRST_PAGE)).isNotNull();
        assertThat(auditLogRepository.search(null, "TRANSACTION", FIRST_PAGE).getContent())
                .allMatch(a -> a.getEntityType().equals("TRANSACTION"));
        assertThat(auditLogRepository.search("system", null, FIRST_PAGE)).isNotNull();
        assertThat(auditLogRepository.search("system", "ACCOUNT", FIRST_PAGE)).isNotNull();
    }

    @Test
    void reconciliationSearchRunsWithAndWithoutAStatus() {
        assertThat(batchRepository.search(null, FIRST_PAGE)).isNotNull();
        assertThat(batchRepository.search(ReconciliationStatus.COMPLETED, FIRST_PAGE).getContent())
                .allMatch(b -> b.getStatus() == ReconciliationStatus.COMPLETED);
    }
}
