package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.AuditLog;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.AuditLogRepository;
import com.ledgerflow.service.EntryLine;
import com.ledgerflow.service.PostingCommand;
import com.ledgerflow.service.PostingService;
import java.math.BigDecimal;
import java.util.List;
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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postingWritesAreVisibleAndAuditLogRejectsMutationAtTheDatabaseLevel() {
        Account debitAccount = newAccount("IT Debit Account");
        Account creditAccount = newAccount("IT Credit Account");

        PostingCommand command = new PostingCommand(
                "it-posting-" + UUID.randomUUID(),
                "integration test posting",
                List.of(
                        new EntryLine(debitAccount.getId(), EntryType.DEBIT, new BigDecimal("40.00")),
                        new EntryLine(creditAccount.getId(), EntryType.CREDIT, new BigDecimal("40.00"))));

        postingService.post(command);

        Account reloadedDebit = accountRepository.findById(debitAccount.getId()).orElseThrow();
        assertThat(reloadedDebit.getBalance()).isEqualByComparingTo("40.00");

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
                "INSERT INTO transactions (idempotency_key, description, status) VALUES (?, ?, ?)",
                key, "first", "POSTED");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO transactions (idempotency_key, description, status) VALUES (?, ?, ?)",
                        key, "second", "POSTED"))
                .isInstanceOf(DataAccessException.class);
    }

    private Account newAccount(String name) {
        Account account = new Account();
        account.setName(name);
        account.setCurrency("USD");
        account.setType(AccountType.ASSET);
        return accountRepository.save(account);
    }
}
