package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillLine;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.TaxRate;
import com.ledgerflow.exception.BillException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.BillDraft;
import com.ledgerflow.service.BillLineDraft;
import com.ledgerflow.service.BillService;
import com.ledgerflow.service.ContactDraft;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.service.TaxRateDraft;
import com.ledgerflow.service.TaxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bills, exercised against real Postgres -- the mirror image of {@link
 * InvoiceIntegrationTest}. The interesting parts are the ones a mocked
 * repository could not prove: that posting really does move the real
 * accounts a bill's own lines named, plus Tax Receivable and Accounts
 * Payable, by the exact amounts InvoiceTotalsCalculator computed; and that
 * the duplicate-vendor-bill guard actually stops the same vendor bill being
 * entered twice.
 */
class BillIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BillService billService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private TaxRateService taxRateService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionRepository transactionRepository;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void creatingADraftComputesLiveTotalsFromItsLines() {
        Contact vendor = vendor();
        TaxRate vat = taxRateService.create(new TaxRateDraft("Bill VAT " + UUID.randomUUID(), new BigDecimal("15")));
        Long expenseAccountId = expenseAccount().getId();

        Bill bill = billService.create(new BillDraft(
                vendor.getId(),
                "VEND-REF-" + UUID.randomUUID(),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 10),
                "first bill",
                List.of(
                        new BillLineDraft(expenseAccountId, null, "Consulting", new BigDecimal("2"), new BigDecimal("100.00"), vat.getId()),
                        new BillLineDraft(expenseAccountId, null, "Materials", BigDecimal.ONE, new BigDecimal("50.00"), null))));

        assertThat(bill.getStatus()).isEqualTo(BillStatus.DRAFT);
        assertThat(bill.getBillNumber()).isNull();

        List<BillLine> lines = billService.getLines(bill.getId());
        assertThat(lines).hasSize(2);

        var totals = billService.totalsFor(lines);
        // 2 x 100 = 200 + 15% tax (30.00) = 230.00, plus 1 x 50 untaxed.
        assertThat(totals.subtotal()).isEqualByComparingTo("250.00");
        assertThat(totals.taxTotal()).isEqualByComparingTo("30.00");
        assertThat(totals.grandTotal()).isEqualByComparingTo("280.00");
    }

    @Test
    void updatingADraftReplacesItsLinesEntirely() {
        Bill bill = draftBill(vendor(), "50.00");
        assertThat(billService.getLines(bill.getId())).hasSize(1);

        Long expenseAccountId = expenseAccount().getId();
        billService.update(
                bill.getId(),
                new BillDraft(
                        bill.getContactId(),
                        bill.getVendorReference(),
                        bill.getBillDate(),
                        bill.getDueDate(),
                        null,
                        List.of(
                                new BillLineDraft(expenseAccountId, null, "Replaced A", BigDecimal.ONE, new BigDecimal("10.00"), null),
                                new BillLineDraft(expenseAccountId, null, "Replaced B", BigDecimal.ONE, new BigDecimal("20.00"), null))));

        List<BillLine> lines = billService.getLines(bill.getId());
        assertThat(lines).hasSize(2);
        assertThat(billService.totalsFor(lines).subtotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void onlyADraftCanBeEditedPostedForTheFirstTimeOrDeleted() {
        Bill bill = draftBill(vendor(), "100.00");
        billService.post(bill.getId());

        assertThatThrownBy(() -> billService.update(bill.getId(), sameShapeDraft(bill)))
                .isInstanceOf(BillException.class)
                .satisfies(e -> assertThat(((BillException) e).getCode()).isEqualTo("BILL_NOT_EDITABLE"));

        assertThatThrownBy(() -> billService.delete(bill.getId()))
                .isInstanceOf(BillException.class)
                .satisfies(e -> assertThat(((BillException) e).getCode()).isEqualTo("BILL_NOT_EDITABLE"));
    }

    @Test
    void postingADraftDrawsANumberAndMovesTheAccountsItPostedTo() {
        Account expense = expenseAccount();
        var taxReceivable = accountRepository.findBySystemRole(SystemAccountRole.TAX_RECEIVABLE).orElseThrow();
        var accountsPayable = accountRepository.findBySystemRole(SystemAccountRole.ACCOUNTS_PAYABLE).orElseThrow();

        BigDecimal expenseBefore = balanceService.getBalance(expense.getId()).balance();
        BigDecimal taxBefore = balanceService.getBalance(taxReceivable.getId()).balance();
        BigDecimal apBefore = balanceService.getBalance(accountsPayable.getId()).balance();

        TaxRate vat = taxRateService.create(new TaxRateDraft("Post Test VAT " + UUID.randomUUID(), new BigDecimal("10")));
        Bill bill = billService.create(new BillDraft(
                vendor().getId(),
                "VEND-" + UUID.randomUUID(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                null,
                List.of(new BillLineDraft(expense.getId(), null, "Widgets", BigDecimal.TEN, new BigDecimal("20.00"), vat.getId()))));
        // 10 x 20 = 200 subtotal, 10% tax = 20, grand total 220.

        Bill posted = billService.post(bill.getId());

        assertThat(posted.getStatus()).isEqualTo(BillStatus.OPEN);
        assertThat(posted.getBillNumber()).matches("BILL-\\d{5}");
        assertThat(posted.getPostedTransactionId()).isNotNull();

        // DEBIT increases the expense account and Tax Receivable (both
        // assets/expenses under this ledger's convention); CREDIT increases
        // Accounts Payable, a liability, shown negative.
        assertThat(balanceService.getBalance(expense.getId()).balance())
                .isEqualByComparingTo(expenseBefore.add(new BigDecimal("200.00")));
        assertThat(balanceService.getBalance(taxReceivable.getId()).balance())
                .isEqualByComparingTo(taxBefore.add(new BigDecimal("20.00")));
        assertThat(balanceService.getBalance(accountsPayable.getId()).balance())
                .isEqualByComparingTo(apBefore.subtract(new BigDecimal("220.00")));
    }

    @Test
    void postingABillWithNothingOwedIsRefused() {
        Bill bill = billService.create(new BillDraft(
                vendor().getId(),
                "VEND-" + UUID.randomUUID(),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                null,
                List.of(new BillLineDraft(expenseAccount().getId(), null, "Free sample", BigDecimal.ONE, BigDecimal.ZERO, null))));

        assertThatThrownBy(() -> billService.post(bill.getId()))
                .isInstanceOf(BillException.class)
                .satisfies(e -> assertThat(((BillException) e).getCode()).isEqualTo("NOTHING_TO_BILL"));
    }

    @Test
    void voidingAnOpenBillReversesItsPostingAndMarksItVoid() {
        Bill bill = draftBill(vendor(), "75.00");
        Bill posted = billService.post(bill.getId());

        Bill voided = billService.voidBill(posted.getId(), "entered in error");

        assertThat(voided.getStatus()).isEqualTo(BillStatus.VOID);
        assertThat(transactionRepository.findByReversalOfTransactionId(posted.getPostedTransactionId())).isPresent();
    }

    @Test
    void onlyAnOpenBillCanBeVoided() {
        Bill draft = draftBill(vendor(), "40.00");

        assertThatThrownBy(() -> billService.voidBill(draft.getId(), null))
                .isInstanceOf(BillException.class)
                .satisfies(e -> assertThat(((BillException) e).getCode()).isEqualTo("BILL_NOT_VOIDABLE"));
    }

    @Test
    void aPurelyCustomerContactCannotBeBilled() {
        Contact customer = contactService.create(new ContactDraft(
                ContactType.CUSTOMER, "Customer Only " + UUID.randomUUID(), null, null, null, null, null, null, null,
                null, null, null));

        assertThatThrownBy(() -> billService.create(new BillDraft(
                        customer.getId(),
                        "VEND-" + UUID.randomUUID(),
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        null,
                        List.of(new BillLineDraft(expenseAccount().getId(), null, "x", BigDecimal.ONE, BigDecimal.TEN, null)))))
                .isInstanceOf(BillException.class)
                .satisfies(e -> assertThat(((BillException) e).getCode()).isEqualTo("CONTACT_NOT_A_VENDOR"));
    }

    @Test
    void enteringTheSameVendorBillTwiceIsRefused() {
        Contact vendor = vendor();
        String reference = "DUP-" + UUID.randomUUID();
        billService.create(new BillDraft(
                vendor.getId(),
                reference,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                List.of(new BillLineDraft(expenseAccount().getId(), null, "Line", BigDecimal.ONE, new BigDecimal("10.00"), null))));

        assertThatThrownBy(() -> billService.create(new BillDraft(
                        vendor.getId(),
                        reference,
                        LocalDate.of(2026, 6, 2),
                        LocalDate.of(2026, 6, 30),
                        null,
                        List.of(new BillLineDraft(expenseAccount().getId(), null, "Line", BigDecimal.ONE, new BigDecimal("10.00"), null)))))
                .isInstanceOf(BillException.class)
                .satisfies(e -> assertThat(((BillException) e).getCode()).isEqualTo("DUPLICATE_VENDOR_BILL"));
    }

    @Test
    void voidingFreesTheVendorReferenceForReuse() {
        Contact vendor = vendor();
        String reference = "REUSE-" + UUID.randomUUID();
        Bill first = billService.create(new BillDraft(
                vendor.getId(),
                reference,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                List.of(new BillLineDraft(expenseAccount().getId(), null, "Line", BigDecimal.ONE, new BigDecimal("10.00"), null))));
        Bill posted = billService.post(first.getId());
        billService.voidBill(posted.getId(), "wrong amount, re-entering");

        Bill second = billService.create(new BillDraft(
                vendor.getId(),
                reference,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                List.of(new BillLineDraft(expenseAccount().getId(), null, "Line", BigDecimal.ONE, new BigDecimal("12.00"), null))));

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void theSweeperFinishesABillStuckAfterPostingButBeforeTheWriteBack() {
        Bill bill = draftBill(vendor(), "60.00");
        Bill posted = billService.post(bill.getId());
        Long realTransactionId = posted.getPostedTransactionId();

        // Simulate the crash window: the posting happened (the transaction
        // below is real and already committed), but the write-back of
        // posted_transaction_id never landed.
        owner.update("UPDATE bills SET posted_transaction_id = NULL WHERE id = ?", bill.getId());

        billService.retryStuckPosts();

        Bill healed = billService.get(bill.getId());
        // Re-linked to the SAME transaction the first attempt already
        // posted -- post()'s idempotency key means retrying never posts a
        // second journal for one bill.
        assertThat(healed.getPostedTransactionId()).isEqualTo(realTransactionId);
    }

    private Contact vendor() {
        return contactService.create(new ContactDraft(
                ContactType.VENDOR, "Bill Test Vendor " + UUID.randomUUID(), null, null, null, null, null, null, null,
                null, null, null));
    }

    private Account expenseAccount() {
        return accountRepository.findAll().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE && a.isPostable())
                .findFirst()
                .orElseThrow();
    }

    private Bill draftBill(Contact vendor, String amount) {
        return billService.create(new BillDraft(
                vendor.getId(),
                "VEND-" + UUID.randomUUID(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                List.of(new BillLineDraft(expenseAccount().getId(), null, "Line", BigDecimal.ONE, new BigDecimal(amount), null))));
    }

    private BillDraft sameShapeDraft(Bill bill) {
        return new BillDraft(
                bill.getContactId(),
                bill.getVendorReference(),
                bill.getBillDate(),
                bill.getDueDate(),
                bill.getNotes(),
                List.of(new BillLineDraft(expenseAccount().getId(), null, "Unchanged", BigDecimal.ONE, BigDecimal.TEN, null)));
    }
}
