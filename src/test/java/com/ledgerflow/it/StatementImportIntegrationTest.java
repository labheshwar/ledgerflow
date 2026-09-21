package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.BankAccount;
import com.ledgerflow.domain.StatementImport;
import com.ledgerflow.domain.StatementImportStatus;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.exception.MasterDataException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.BankAccountDraft;
import com.ledgerflow.service.BankAccountService;
import com.ledgerflow.service.ColumnMapping;
import com.ledgerflow.service.StatementImportService;
import com.ledgerflow.service.UploadedStatement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Bank accounts and the CSV import wizard, exercised against real
 * Postgres, MinIO and RabbitMQ. Preview genuinely goes through the real
 * publish -> broker -> worker round trip, the same shape {@code
 * ReconciliationIntegrationTest} already proves out, rather than calling
 * the listener method directly in-process.
 */
class StatementImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private StatementImportService statementImportService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void aHeadingAccountCannotBackABankAccount() {
        Account heading = newHeadingAccount();

        assertThatThrownBy(() -> bankAccountService.create(new BankAccountDraft(heading.getId(), "Bad Bank", null)))
                .isInstanceOf(MasterDataException.class)
                .satisfies(e -> assertThat(((MasterDataException) e).getCode()).isEqualTo("ACCOUNT_NOT_POSTABLE"));
    }

    @Test
    void uploadingParsesTheHeaderRowForTheMappingStep() {
        BankAccount bankAccount = bankAccount();
        byte[] csv = csv(
                "Posted Date,Details,Amount,Ref",
                "2026-01-10,Coffee shop,-4.50,ref-1",
                "2026-01-11,Paycheck,2000.00,ref-2");

        UploadedStatement uploaded = statementImportService.upload(bankAccount.getId(), "statement.csv", csv);

        assertThat(uploaded.headers()).containsExactly("Posted Date", "Details", "Amount", "Ref");
    }

    @Test
    void previewingComputesNewRowsOverRealRabbitMq() {
        BankAccount bankAccount = bankAccount();
        byte[] csv = csv(
                "Posted Date,Details,Amount,Ref",
                "2026-01-10,Coffee shop,-4.50,ref-a-" + UUID.randomUUID(),
                "2026-01-11,Paycheck,2000.00,ref-b-" + UUID.randomUUID());
        UploadedStatement uploaded = statementImportService.upload(bankAccount.getId(), "statement.csv", csv);

        statementImportService.requestPreview(
                uploaded.statementImport().getId(), new ColumnMapping("Posted Date", "Details", "Amount", "Ref"));

        awaitCondition(() -> statementImportService.get(uploaded.statementImport().getId()).getStatus()
                == StatementImportStatus.PREVIEWED);

        var previewed = statementImportService.get(uploaded.statementImport().getId());
        assertThat(previewed.getTotalRows()).isEqualTo(2);
        assertThat(previewed.getNewRows()).isEqualTo(2);
        assertThat(previewed.getDuplicateRows()).isZero();
        assertThat(previewed.getErrorRows()).isZero();

        var lines = statementImportService
                .previewLines(previewed.getId(), PageRequest.of(0, 10))
                .getContent();
        assertThat(lines).extracting(l -> l.getDescription()).containsExactlyInAnyOrder("Coffee shop", "Paycheck");
        assertThat(lines).allMatch(l -> !l.isCommitted());
    }

    @Test
    void committingMakesStagedRowsPartOfTheStatementAndReimportingDedupes() {
        BankAccount bankAccount = bankAccount();
        String ref1 = "dedupe-a-" + UUID.randomUUID();
        String ref2 = "dedupe-b-" + UUID.randomUUID();
        byte[] csv = csv(
                "Posted Date,Details,Amount,Ref",
                "2026-02-01,Groceries,-55.20," + ref1,
                "2026-02-02,Refund,20.00," + ref2);

        Long firstImportId = previewToCompletion(bankAccount, csv).getId();
        var committed = statementImportService.commit(firstImportId);
        assertThat(committed.getStatus()).isEqualTo(StatementImportStatus.COMMITTED);

        var statement = statementImportService
                .committedLines(bankAccount.getId(), PageRequest.of(0, 10))
                .getContent();
        assertThat(statement).extracting(l -> l.getDescription()).containsExactlyInAnyOrder("Groceries", "Refund");

        // Re-importing the exact same file: every row already exists, so nothing new lands.
        var secondPreview = previewToCompletion(bankAccount, csv);
        assertThat(secondPreview.getNewRows()).isZero();
        assertThat(secondPreview.getDuplicateRows()).isEqualTo(2);
    }

    @Test
    void aRowWithAnUnparseableDateIsCountedAsAnErrorRatherThanFailingTheWholeImport() {
        BankAccount bankAccount = bankAccount();
        byte[] csv = csv(
                "Posted Date,Details,Amount,Ref",
                "not-a-date,Bad row,-1.00,err-" + UUID.randomUUID(),
                "2026-03-01,Good row,-2.00,ok-" + UUID.randomUUID());

        var previewed = previewToCompletion(bankAccount, csv);

        assertThat(previewed.getTotalRows()).isEqualTo(2);
        assertThat(previewed.getErrorRows()).isEqualTo(1);
        assertThat(previewed.getNewRows()).isEqualTo(1);
    }

    @Test
    void withNoExternalIdColumnTheSameRowComputesTheSameFallbackId() {
        BankAccount bankAccount = bankAccount();
        String description = "Rent " + UUID.randomUUID();
        byte[] csv = csv("Posted Date,Details,Amount", "2026-04-01," + description + ",-1200.00");

        Long firstImportId = previewToCompletion(bankAccount, csv, new ColumnMapping("Posted Date", "Details", "Amount", null))
                .getId();
        statementImportService.commit(firstImportId);

        // Same file, same row, no external id column: the computed fallback
        // hash must recognize it as the same transaction, not a new one.
        var secondPreview = previewToCompletion(bankAccount, csv, new ColumnMapping("Posted Date", "Details", "Amount", null));
        assertThat(secondPreview.getNewRows()).isZero();
        assertThat(secondPreview.getDuplicateRows()).isEqualTo(1);
    }

    private StatementImport previewToCompletion(BankAccount bankAccount, byte[] csv) {
        return previewToCompletion(bankAccount, csv, new ColumnMapping("Posted Date", "Details", "Amount", "Ref"));
    }

    private StatementImport previewToCompletion(BankAccount bankAccount, byte[] csv, ColumnMapping mapping) {
        UploadedStatement uploaded = statementImportService.upload(bankAccount.getId(), "statement.csv", csv);
        statementImportService.requestPreview(uploaded.statementImport().getId(), mapping);
        awaitCondition(() -> {
            var status = statementImportService.get(uploaded.statementImport().getId()).getStatus();
            return status == StatementImportStatus.PREVIEWED
                    || status == StatementImportStatus.FAILED;
        });
        return statementImportService.get(uploaded.statementImport().getId());
    }

    private byte[] csv(String... lines) {
        return String.join("\r\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private BankAccount bankAccount() {
        Account cash = accountRepository.findBySystemRole(SystemAccountRole.CASH).orElseThrow();
        return bankAccountService.create(new BankAccountDraft(cash.getId(), "Test Bank " + UUID.randomUUID(), "1234"));
    }

    private Account newHeadingAccount() {
        Account account = new Account();
        account.setOrgId(DEMO_ORG_ID);
        account.setCode("H" + UUID.randomUUID().toString().substring(0, 8));
        account.setName("Heading " + UUID.randomUUID());
        account.setCurrency("USD");
        account.setType(AccountType.ASSET);
        account.setPostable(false);
        return accountRepository.save(account);
    }
}
