package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceLine;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.TaxRate;
import com.ledgerflow.exception.InvoiceException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.ContactDraft;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.service.InvoiceDraft;
import com.ledgerflow.service.InvoiceLineDraft;
import com.ledgerflow.service.InvoiceService;
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
 * Invoices, exercised against real Postgres. The interesting parts are the
 * ones a mocked repository could not prove: that sending really does move
 * three real account balances by the exact amounts InvoiceTotalsCalculator
 * computed, and that a crash between posting and recording the result is
 * recoverable rather than a stuck, half-sent invoice forever.
 */
class InvoiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

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
        Contact customer = customer();
        TaxRate vat = taxRateService.create(new TaxRateDraft("VAT " + UUID.randomUUID(), new BigDecimal("15")));

        Invoice invoice = invoiceService.create(new InvoiceDraft(
                customer.getId(),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 10),
                "first invoice",
                List.of(
                        new InvoiceLineDraft(null, "Consulting", new BigDecimal("2"), new BigDecimal("100.00"), vat.getId()),
                        new InvoiceLineDraft(null, "Materials", BigDecimal.ONE, new BigDecimal("50.00"), null))));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getInvoiceNumber()).isNull();

        List<InvoiceLine> lines = invoiceService.getLines(invoice.getId());
        assertThat(lines).hasSize(2);

        var totals = invoiceService.totalsFor(lines);
        // 2 x 100 = 200 + 15% tax (30.00) = 230.00, plus 1 x 50 untaxed.
        assertThat(totals.subtotal()).isEqualByComparingTo("250.00");
        assertThat(totals.taxTotal()).isEqualByComparingTo("30.00");
        assertThat(totals.grandTotal()).isEqualByComparingTo("280.00");
    }

    @Test
    void updatingADraftReplacesItsLinesEntirely() {
        Invoice invoice = draftInvoice(customer(), "50.00");
        assertThat(invoiceService.getLines(invoice.getId())).hasSize(1);

        invoiceService.update(
                invoice.getId(),
                new InvoiceDraft(
                        invoice.getContactId(),
                        invoice.getIssueDate(),
                        invoice.getDueDate(),
                        null,
                        List.of(
                                new InvoiceLineDraft(null, "Replaced A", BigDecimal.ONE, new BigDecimal("10.00"), null),
                                new InvoiceLineDraft(null, "Replaced B", BigDecimal.ONE, new BigDecimal("20.00"), null))));

        List<InvoiceLine> lines = invoiceService.getLines(invoice.getId());
        assertThat(lines).hasSize(2);
        assertThat(invoiceService.totalsFor(lines).subtotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void onlyADraftCanBeEditedSentForTheFirstTimeOrDeleted() {
        Invoice invoice = draftInvoice(customer(), "100.00");
        invoiceService.send(invoice.getId());

        assertThatThrownBy(() -> invoiceService.update(invoice.getId(), sameShapeDraft(invoice)))
                .isInstanceOf(InvoiceException.class)
                .satisfies(e -> assertThat(((InvoiceException) e).getCode()).isEqualTo("INVOICE_NOT_EDITABLE"));

        assertThatThrownBy(() -> invoiceService.delete(invoice.getId()))
                .isInstanceOf(InvoiceException.class)
                .satisfies(e -> assertThat(((InvoiceException) e).getCode()).isEqualTo("INVOICE_NOT_EDITABLE"));
    }

    @Test
    void sendingADraftDrawsANumberAndMovesTheThreeAccountsItPostedTo() {
        var ar = accountRepository.findBySystemRole(SystemAccountRole.ACCOUNTS_RECEIVABLE).orElseThrow();
        var revenue = accountRepository.findBySystemRole(SystemAccountRole.SALES_REVENUE).orElseThrow();
        var taxPayable = accountRepository.findBySystemRole(SystemAccountRole.TAX_PAYABLE).orElseThrow();

        BigDecimal arBefore = balanceService.getBalance(ar.getId()).balance();
        BigDecimal revenueBefore = balanceService.getBalance(revenue.getId()).balance();
        BigDecimal taxBefore = balanceService.getBalance(taxPayable.getId()).balance();

        TaxRate vat = taxRateService.create(new TaxRateDraft("Send Test VAT " + UUID.randomUUID(), new BigDecimal("10")));
        Invoice invoice = invoiceService.create(new InvoiceDraft(
                customer().getId(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                null,
                List.of(new InvoiceLineDraft(null, "Widgets", BigDecimal.TEN, new BigDecimal("20.00"), vat.getId()))));
        // 10 x 20 = 200 subtotal, 10% tax = 20, grand total 220.

        Invoice sent = invoiceService.send(invoice.getId());

        assertThat(sent.getStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(sent.getInvoiceNumber()).matches("INV-\\d{5}");
        assertThat(sent.getPostedTransactionId()).isNotNull();

        // DEBIT increases AR (an asset) under this ledger's convention;
        // CREDIT decreases Revenue and Tax Payable, both shown negative.
        assertThat(balanceService.getBalance(ar.getId()).balance()).isEqualByComparingTo(arBefore.add(new BigDecimal("220.00")));
        assertThat(balanceService.getBalance(revenue.getId()).balance())
                .isEqualByComparingTo(revenueBefore.subtract(new BigDecimal("200.00")));
        assertThat(balanceService.getBalance(taxPayable.getId()).balance())
                .isEqualByComparingTo(taxBefore.subtract(new BigDecimal("20.00")));
    }

    @Test
    void sendingAnInvoiceWithNothingToChargeIsRefused() {
        Invoice invoice = invoiceService.create(new InvoiceDraft(
                customer().getId(),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                null,
                List.of(new InvoiceLineDraft(null, "Free sample", BigDecimal.ONE, BigDecimal.ZERO, null))));

        assertThatThrownBy(() -> invoiceService.send(invoice.getId()))
                .isInstanceOf(InvoiceException.class)
                .satisfies(e -> assertThat(((InvoiceException) e).getCode()).isEqualTo("NOTHING_TO_INVOICE"));
    }

    @Test
    void voidingASentInvoiceReversesItsPostingAndMarksItVoid() {
        Invoice invoice = draftInvoice(customer(), "75.00");
        Invoice sent = invoiceService.send(invoice.getId());

        Invoice voided = invoiceService.voidInvoice(sent.getId(), "customer cancelled");

        assertThat(voided.getStatus()).isEqualTo(InvoiceStatus.VOID);
        assertThat(transactionRepository.findByReversalOfTransactionId(sent.getPostedTransactionId())).isPresent();
    }

    @Test
    void onlyASentInvoiceCanBeVoided() {
        Invoice draft = draftInvoice(customer(), "40.00");

        assertThatThrownBy(() -> invoiceService.voidInvoice(draft.getId(), null))
                .isInstanceOf(InvoiceException.class)
                .satisfies(e -> assertThat(((InvoiceException) e).getCode()).isEqualTo("INVOICE_NOT_VOIDABLE"));
    }

    @Test
    void aPurelyVendorContactCannotBeInvoiced() {
        Contact vendor = contactService.create(new ContactDraft(
                ContactType.VENDOR, "Vendor Only " + UUID.randomUUID(), null, null, null, null, null, null, null,
                null, null, null));

        assertThatThrownBy(() -> invoiceService.create(new InvoiceDraft(
                        vendor.getId(),
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        null,
                        List.of(new InvoiceLineDraft(null, "x", BigDecimal.ONE, BigDecimal.TEN, null)))))
                .isInstanceOf(InvoiceException.class)
                .satisfies(e -> assertThat(((InvoiceException) e).getCode()).isEqualTo("CONTACT_NOT_A_CUSTOMER"));
    }

    @Test
    void theSweeperFinishesAnInvoiceStuckAfterPostingButBeforeTheWriteBack() {
        Invoice invoice = draftInvoice(customer(), "60.00");
        Invoice sent = invoiceService.send(invoice.getId());
        Long realTransactionId = sent.getPostedTransactionId();

        // Simulate the crash window: the posting happened (the transaction
        // below is real and already committed), but the write-back of
        // posted_transaction_id never landed.
        owner.update("UPDATE invoices SET posted_transaction_id = NULL WHERE id = ?", invoice.getId());

        invoiceService.retryStuckSends();

        Invoice healed = invoiceService.get(invoice.getId());
        // Re-linked to the SAME transaction the first attempt already
        // posted -- send()'s idempotency key means retrying never posts a
        // second journal for one invoice.
        assertThat(healed.getPostedTransactionId()).isEqualTo(realTransactionId);
    }

    private Contact customer() {
        return contactService.create(new ContactDraft(
                ContactType.CUSTOMER, "Invoice Test Customer " + UUID.randomUUID(), null, null, null, null, null,
                null, null, null, null, null));
    }

    private Invoice draftInvoice(Contact customer, String amount) {
        return invoiceService.create(new InvoiceDraft(
                customer.getId(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                null,
                List.of(new InvoiceLineDraft(null, "Line", BigDecimal.ONE, new BigDecimal(amount), null))));
    }

    private InvoiceDraft sameShapeDraft(Invoice invoice) {
        return new InvoiceDraft(
                invoice.getContactId(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getNotes(),
                List.of(new InvoiceLineDraft(null, "Unchanged", BigDecimal.ONE, BigDecimal.TEN, null)));
    }
}
