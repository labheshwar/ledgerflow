package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.BankAccount;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.StatementImportStatus;
import com.ledgerflow.domain.StatementLine;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.ReconciliationException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.EntryRepository;
import com.ledgerflow.service.BankAccountDraft;
import com.ledgerflow.service.BankAccountService;
import com.ledgerflow.service.ColumnMapping;
import com.ledgerflow.service.ContactDraft;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.service.InvoiceDraft;
import com.ledgerflow.service.InvoiceLineDraft;
import com.ledgerflow.service.InvoiceService;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.MatchKind;
import com.ledgerflow.service.MatchSuggestion;
import com.ledgerflow.service.PaymentService;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.service.ReconciliationService;
import com.ledgerflow.service.StatementImportService;
import com.ledgerflow.service.UploadedStatement;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * The reconciliation workspace, exercised against real Postgres and
 * RabbitMQ (the statement import wizard behind {@code committedLine} is
 * the same real publish -> broker -> worker round trip {@code
 * StatementImportIntegrationTest} already proves out).
 *
 * Every test gets its own freshly created GL account behind its bank
 * account, never a system-role one -- the same reasoning {@code
 * PaymentIntegrationTest}'s own header comment gives: a shared account
 * that background sweepers and every other test in the suite can also
 * post to is not safe to make assertions about the *candidates* for, only
 * about one transaction's own entries.
 */
class ReconciliationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private StatementImportService statementImportService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private PostingService postingService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Test
    void matchingToASuggestedEntrySetsTheLineAndRefusesToShareThatEntryWithAnotherLine() {
        Account bankGl = freshAccount(AccountType.ASSET);
        Account revenue = freshAccount(AccountType.REVENUE);
        BankAccount bankAccount = bankAccount(bankGl);

        Transaction posted = postingService.post(JournalBuilder.forDate(LocalDate.of(2026, 5, 10))
                .withIdempotencyKey("RECON-TEST:" + UUID.randomUUID())
                .describedAs("Wire from Acme Corp")
                .debit(bankGl.getId(), Money.of(new BigDecimal("250.00"), "USD"))
                .credit(revenue.getId(), Money.of(new BigDecimal("250.00"), "USD"))
                .build());
        Long entryId = entryOn(posted.getId(), bankGl.getId()).getId();

        StatementLine line = committedLine(bankAccount, LocalDate.of(2026, 5, 10), "ACME CORP WIRE", new BigDecimal("250.00"));

        List<MatchSuggestion> suggestions = reconciliationService.suggestionsFor(line.getId());
        assertThat(suggestions).anyMatch(s -> s.kind() == MatchKind.ENTRY && s.id().equals(entryId));

        StatementLine matched = reconciliationService.matchToEntry(line.getId(), entryId);
        assertThat(matched.getMatchedEntryId()).isEqualTo(entryId);
        assertThat(matched.getMatchedAt()).isNotNull();

        StatementLine secondLine = committedLine(bankAccount, LocalDate.of(2026, 5, 10), "ACME CORP WIRE", new BigDecimal("250.00"));
        assertThatThrownBy(() -> reconciliationService.matchToEntry(secondLine.getId(), entryId))
                .isInstanceOf(ReconciliationException.class)
                .satisfies(e -> assertThat(((ReconciliationException) e).getCode()).isEqualTo("ENTRY_ALREADY_MATCHED"));
    }

    @Test
    void matchingToAnEntryOnAnotherAccountIsRefused() {
        Account bankGl = freshAccount(AccountType.ASSET);
        Account otherAccount = freshAccount(AccountType.ASSET);
        Account revenue = freshAccount(AccountType.REVENUE);
        BankAccount bankAccount = bankAccount(bankGl);

        Transaction posted = postingService.post(JournalBuilder.forDate(LocalDate.of(2026, 5, 11))
                .withIdempotencyKey("RECON-TEST:" + UUID.randomUUID())
                .describedAs("Unrelated posting")
                .debit(otherAccount.getId(), Money.of(new BigDecimal("40.00"), "USD"))
                .credit(revenue.getId(), Money.of(new BigDecimal("40.00"), "USD"))
                .build());
        Long entryOnOtherAccount = entryOn(posted.getId(), otherAccount.getId()).getId();

        StatementLine line = committedLine(bankAccount, LocalDate.of(2026, 5, 11), "Misc", new BigDecimal("40.00"));

        assertThatThrownBy(() -> reconciliationService.matchToEntry(line.getId(), entryOnOtherAccount))
                .isInstanceOf(ReconciliationException.class)
                .satisfies(e -> assertThat(((ReconciliationException) e).getCode()).isEqualTo("ENTRY_WRONG_ACCOUNT"));
    }

    @Test
    void settlingAnOpenInvoiceCreatesAPaymentScopedToThisBankAccountAndMatchesItsCashEntry() {
        Account bankGl = freshAccount(AccountType.ASSET);
        BankAccount bankAccount = bankAccount(bankGl);
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "120.00");

        StatementLine line = committedLine(bankAccount, LocalDate.of(2026, 6, 1), "Customer deposit", new BigDecimal("120.00"));

        List<MatchSuggestion> suggestions = reconciliationService.suggestionsFor(line.getId());
        assertThat(suggestions).anyMatch(s -> s.kind() == MatchKind.INVOICE && s.id().equals(invoice.getId()));

        StatementLine matched = reconciliationService.settle(line.getId(), DocumentType.INVOICE, invoice.getId());

        assertThat(matched.getMatchedEntryId()).isNotNull();
        assertThat(paymentService.amountPaidFor(DocumentType.INVOICE, invoice.getId())).isEqualByComparingTo("120.00");

        Entry cashEntry = entryRepository.findById(matched.getMatchedEntryId()).orElseThrow();
        assertThat(cashEntry.getAccount().getId()).isEqualTo(bankGl.getId());
        assertThat(cashEntry.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(cashEntry.getAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void settlingAnAlreadyFullySettledDocumentIsRefused() {
        Account bankGl = freshAccount(AccountType.ASSET);
        BankAccount bankAccount = bankAccount(bankGl);
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "75.00");

        StatementLine first = committedLine(bankAccount, LocalDate.of(2026, 6, 2), "Deposit 1", new BigDecimal("75.00"));
        reconciliationService.settle(first.getId(), DocumentType.INVOICE, invoice.getId());

        StatementLine second = committedLine(bankAccount, LocalDate.of(2026, 6, 3), "Deposit 2", new BigDecimal("75.00"));
        assertThatThrownBy(() -> reconciliationService.settle(second.getId(), DocumentType.INVOICE, invoice.getId()))
                .isInstanceOf(ReconciliationException.class)
                .satisfies(e -> assertThat(((ReconciliationException) e).getCode()).isEqualTo("DOCUMENT_ALREADY_SETTLED"));
    }

    @Test
    void moneyOutCannotSettleAnInvoiceAndMoneyInCannotSettleABill() {
        Account bankGl = freshAccount(AccountType.ASSET);
        BankAccount bankAccount = bankAccount(bankGl);
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "30.00");

        StatementLine moneyOut = committedLine(bankAccount, LocalDate.of(2026, 6, 4), "Outgoing", new BigDecimal("-30.00"));
        assertThatThrownBy(() -> reconciliationService.settle(moneyOut.getId(), DocumentType.INVOICE, invoice.getId()))
                .isInstanceOf(ReconciliationException.class)
                .satisfies(e -> assertThat(((ReconciliationException) e).getCode()).isEqualTo("AMOUNT_DIRECTION_MISMATCH"));

        StatementLine moneyIn = committedLine(bankAccount, LocalDate.of(2026, 6, 4), "Incoming", new BigDecimal("30.00"));
        assertThatThrownBy(() -> reconciliationService.settle(moneyIn.getId(), DocumentType.BILL, 999_999L))
                .isInstanceOf(ReconciliationException.class)
                .satisfies(e -> assertThat(((ReconciliationException) e).getCode()).isEqualTo("AMOUNT_DIRECTION_MISMATCH"));
    }

    @Test
    void categorizingAnUnmatchedLinePostsAJournalBetweenTheBankAccountAndTheChosenCategory() {
        Account bankGl = freshAccount(AccountType.ASSET);
        Account bankFees = freshAccount(AccountType.EXPENSE);
        BankAccount bankAccount = bankAccount(bankGl);

        StatementLine line = committedLine(bankAccount, LocalDate.of(2026, 6, 5), "Monthly service fee", new BigDecimal("-15.00"));

        StatementLine matched = reconciliationService.categorize(line.getId(), bankFees.getId(), null);

        assertThat(matched.getMatchedEntryId()).isNotNull();
        Entry cashEntry = entryRepository.findById(matched.getMatchedEntryId()).orElseThrow();
        assertThat(cashEntry.getAccount().getId()).isEqualTo(bankGl.getId());
        assertThat(cashEntry.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(cashEntry.getAmount()).isEqualByComparingTo("15.00");

        Entry expenseEntry = entryOn(cashEntry.getTransaction().getId(), bankFees.getId());
        assertThat(expenseEntry.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(expenseEntry.getAmount()).isEqualByComparingTo("15.00");

        assertThatThrownBy(() -> reconciliationService.categorize(line.getId(), bankFees.getId(), null))
                .isInstanceOf(ReconciliationException.class)
                .satisfies(e -> assertThat(((ReconciliationException) e).getCode()).isEqualTo("LINE_ALREADY_MATCHED"));
    }

    @Test
    void unmatchingClearsTheLineButLeavesThePostedEntryAlone() {
        Account bankGl = freshAccount(AccountType.ASSET);
        Account bankFees = freshAccount(AccountType.EXPENSE);
        BankAccount bankAccount = bankAccount(bankGl);
        StatementLine line = committedLine(bankAccount, LocalDate.of(2026, 6, 6), "Card fee", new BigDecimal("-9.00"));
        StatementLine matched = reconciliationService.categorize(line.getId(), bankFees.getId(), null);
        Long entryId = matched.getMatchedEntryId();

        StatementLine unmatched = reconciliationService.unmatch(line.getId());

        assertThat(unmatched.getMatchedEntryId()).isNull();
        assertThat(unmatched.getMatchedAt()).isNull();
        // The journal entry itself is untouched -- unmatching breaks the link, not the posting.
        assertThat(entryRepository.findById(entryId)).isPresent();

        assertThatThrownBy(() -> reconciliationService.unmatch(line.getId()))
                .isInstanceOf(ReconciliationException.class)
                .satisfies(e -> assertThat(((ReconciliationException) e).getCode()).isEqualTo("LINE_NOT_MATCHED"));
    }

    private StatementLine committedLine(BankAccount bankAccount, LocalDate date, String description, BigDecimal amount) {
        byte[] csv = csv(
                "Posted Date,Details,Amount,Ref",
                "%s,%s,%s,ref-%s".formatted(date, description, amount.toPlainString(), UUID.randomUUID()));
        UploadedStatement uploaded = statementImportService.upload(bankAccount.getId(), "statement.csv", csv);
        statementImportService.requestPreview(
                uploaded.statementImport().getId(), new ColumnMapping("Posted Date", "Details", "Amount", "Ref"));

        awaitCondition(() -> statementImportService.get(uploaded.statementImport().getId()).getStatus()
                == StatementImportStatus.PREVIEWED);
        statementImportService.commit(uploaded.statementImport().getId());

        return statementImportService
                .committedLines(bankAccount.getId(), PageRequest.of(0, 20))
                .getContent()
                .stream()
                .filter(l -> l.getDescription().equals(description) && l.getAmount().compareTo(amount) == 0)
                .findFirst()
                .orElseThrow();
    }

    private byte[] csv(String... lines) {
        return String.join("\r\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private BankAccount bankAccount(Account glAccount) {
        return bankAccountService.create(new BankAccountDraft(glAccount.getId(), "Recon Test Bank " + UUID.randomUUID(), null));
    }

    private Account freshAccount(AccountType type) {
        Account account = new Account();
        account.setOrgId(DEMO_ORG_ID);
        account.setCode("R" + UUID.randomUUID().toString().substring(0, 8));
        account.setName("Recon Test " + type + " " + UUID.randomUUID());
        account.setCurrency("USD");
        account.setType(type);
        account.setPostable(true);
        return accountRepository.save(account);
    }

    private Contact customer() {
        return contactService.create(new ContactDraft(
                ContactType.CUSTOMER, "Recon Test Customer " + UUID.randomUUID(), null, null, null, null, null, null,
                null, null, null, null));
    }

    private Invoice sentInvoice(Contact customer, String amount) {
        Invoice draft = invoiceService.create(new InvoiceDraft(
                customer.getId(),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                List.of(new InvoiceLineDraft(null, "Line", BigDecimal.ONE, new BigDecimal(amount), null))));
        return invoiceService.send(draft.getId());
    }

    private Entry entryOn(Long transactionId, Long accountId) {
        return entryRepository.findByTransactionId(transactionId).stream()
                .filter(e -> e.getAccount().getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No entry to account %d on transaction %d".formatted(accountId, transactionId)));
    }
}
