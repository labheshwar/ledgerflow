package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.AuditLog;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.AuditLogRepository;
import com.ledgerflow.money.Money;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.PostingCommand;
import com.ledgerflow.service.PostingService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises real Postgres constraints that no amount of mocking can verify:
 * the append-only trigger on audit_log, and the unique constraint backing
 * idempotency.
 */
class PostingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * These assertions are about database machinery -- the append-only
     * trigger and the idempotency constraint -- so they run as the owner.
     * Through the application's own connection, row-level security would
     * simply filter the target rows away and the UPDATE would match nothing,
     * which looks exactly like the trigger not firing.
     */
    private final JdbcTemplate jdbcTemplate = ownerJdbc();

    @Test
    void postingWritesAreVisibleAndAuditLogRejectsMutationAtTheDatabaseLevel() {
        Account debitAccount = newAccount("IT Debit Account");
        Account creditAccount = newAccount("IT Credit Account");

        PostingCommand command = JournalBuilder.forDate(LocalDate.now(ZoneOffset.UTC))
                .withIdempotencyKey("it-posting-" + UUID.randomUUID())
                .describedAs("integration test posting")
                .debit(debitAccount.getId(), Money.of("40.00", "USD"))
                .credit(creditAccount.getId(), Money.of("40.00", "USD"))
                .build();

        postingService.post(command);

        assertThat(balanceService.getBalance(debitAccount.getId()).balance()).isEqualByComparingTo("40.00");

        AuditLog transactionAudit = auditLogRepository.findAll().stream()
                .filter(a -> "TRANSACTION".equals(a.getEntityType()))
                .reduce((first, second) -> second) // the most recently written one
                .orElseThrow();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE audit_log SET action = 'HACKED' WHERE id = ?", transactionAudit.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", transactionAudit.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void transactionsIdempotencyKeyIsUniqueAtTheDatabaseLevel() {
        String key = "it-unique-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO transactions (org_id, idempotency_key, description, status, txn_date)"
                        + " VALUES (?, ?, ?, ?, current_date)",
                DEMO_ORG_ID, key, "first", "POSTED");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO transactions (org_id, idempotency_key, description, status, txn_date)"
                                + " VALUES (?, ?, ?, ?, current_date)",
                        DEMO_ORG_ID, key, "second", "POSTED"))
                .isInstanceOf(DataAccessException.class);
    }

    private Account newAccount(String name) {
        Account account = new Account();
        account.setName(name);
        account.setCurrency("USD");
        account.setOrgId(DEMO_ORG_ID);
        account.setType(AccountType.ASSET);
        return accountRepository.save(account);
    }
}
