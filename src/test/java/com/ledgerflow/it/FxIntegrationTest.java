package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.exception.FxRateException;
import com.ledgerflow.exception.PaymentException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.FxRateRepository;
import com.ledgerflow.service.ContactDraft;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.service.FxRateService;
import com.ledgerflow.service.InvoiceDraft;
import com.ledgerflow.service.InvoiceLineDraft;
import com.ledgerflow.service.InvoiceService;
import com.ledgerflow.service.PaymentAllocationDraft;
import com.ledgerflow.service.PaymentDraft;
import com.ledgerflow.service.PaymentService;
import com.ledgerflow.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Foreign-currency invoices and their settlement, exercised against real
 * Postgres. A EUR invoice posts its receivable and revenue legs in EUR, at
 * the rate on file for its own issue date; settling it later at a different
 * rate posts the exact realized gain or loss the two rates imply, as one
 * base-currency-only adjustment leg, and settling it at the very same rate
 * posts none at all.
 */
class FxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FxRateService fxRateService;

    @Autowired
    private FxRateRepository fxRateRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void aForeignCurrencyInvoicePostsInItsOwnCurrencyAtTheRateOnFileForItsIssueDate() {
        fxRateService.record("EUR", new BigDecimal("1.10"), LocalDate.of(2026, 3, 1));
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "EUR", LocalDate.of(2026, 3, 1), "100.00");

        Entry ar = entryFor(invoice.getPostedTransactionId(), roleAccountId(SystemAccountRole.ACCOUNTS_RECEIVABLE), EntryType.DEBIT);
        assertThat(ar.getCurrency()).isEqualTo("EUR");
        assertThat(ar.getAmount()).isEqualByComparingTo("100.00");
        assertThat(ar.getBaseAmount()).isEqualByComparingTo("110.00");
        assertThat(ar.getFxRate()).isEqualByComparingTo("1.10");
        assertThat(ar.isFxAdjustment()).isFalse();

        Entry revenue = entryFor(invoice.getPostedTransactionId(), roleAccountId(SystemAccountRole.SALES_REVENUE), EntryType.CREDIT);
        assertThat(revenue.getCurrency()).isEqualTo("EUR");
        assertThat(revenue.getBaseAmount()).isEqualByComparingTo("110.00");
    }

    @Test
    void sendingAForeignCurrencyInvoiceWithNoRateOnFileIsRefused() {
        Contact customer = customer();
        Invoice draft = invoiceService.create(new InvoiceDraft(
                customer.getId(),
                LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 4, 5),
                null,
                List.of(new InvoiceLineDraft(null, "Consulting", BigDecimal.ONE, new BigDecimal("50.00"), null)),
                "GBP"));

        assertThatThrownBy(() -> invoiceService.send(draft.getId()))
                .isInstanceOf(FxRateException.class)
                .satisfies(e -> assertThat(((FxRateException) e).getCode()).isEqualTo("MISSING_FX_RATE"));
    }

    @Test
    void settlingAtTheSameRateThatFedTheInvoicePostsNoFxAdjustmentAtAll() {
        fxRateService.record("EUR", new BigDecimal("1.10"), LocalDate.of(2026, 3, 10));
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "EUR", LocalDate.of(2026, 3, 10), "200.00");

        Payment payment = paymentService.create(new PaymentDraft(
                customer.getId(),
                PaymentDirection.RECEIVED,
                LocalDate.of(2026, 3, 10),
                new BigDecimal("200.00"),
                null,
                List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("200.00")))));

        assertThat(transactionService.getEntries(payment.getPostedTransactionId()))
                .noneMatch(Entry::isFxAdjustment);
        assertThat(paymentService.amountPaidFor(DocumentType.INVOICE, invoice.getId())).isEqualByComparingTo("200.00");
    }

    @Test
    void settlingAtALowerRateThanTheInvoicePostsARealizedLoss() {
        fxRateService.record("EUR", new BigDecimal("1.10"), LocalDate.of(2026, 3, 15));
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "EUR", LocalDate.of(2026, 3, 15), "100.00");

        fxRateService.record("EUR", new BigDecimal("1.05"), LocalDate.of(2026, 4, 15));
        Payment payment = paymentService.create(new PaymentDraft(
                customer.getId(),
                PaymentDirection.RECEIVED,
                LocalDate.of(2026, 4, 15),
                new BigDecimal("100.00"),
                null,
                List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("100.00")))));

        Long txnId = payment.getPostedTransactionId();
        Entry cash = entryFor(txnId, roleAccountId(SystemAccountRole.CASH), EntryType.DEBIT);
        assertThat(cash.getBaseAmount()).isEqualByComparingTo("105.00");
        Entry ar = entryFor(txnId, roleAccountId(SystemAccountRole.ACCOUNTS_RECEIVABLE), EntryType.CREDIT);
        assertThat(ar.getBaseAmount()).isEqualByComparingTo("110.00");
        assertThat(ar.getFxRate()).isEqualByComparingTo("1.10");

        Entry fxLoss = entryFor(txnId, roleAccountId(SystemAccountRole.FX_GAIN_LOSS), EntryType.DEBIT);
        assertThat(fxLoss.getBaseAmount()).isEqualByComparingTo("5.00");
        assertThat(fxLoss.getCurrency()).isEqualTo("USD");
        assertThat(fxLoss.isFxAdjustment()).isTrue();
    }

    @Test
    void settlingAtAHigherRateThanTheInvoicePostsARealizedGain() {
        fxRateService.record("EUR", new BigDecimal("1.10"), LocalDate.of(2026, 3, 20));
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "EUR", LocalDate.of(2026, 3, 20), "100.00");

        fxRateService.record("EUR", new BigDecimal("1.20"), LocalDate.of(2026, 4, 20));
        Payment payment = paymentService.create(new PaymentDraft(
                customer.getId(),
                PaymentDirection.RECEIVED,
                LocalDate.of(2026, 4, 20),
                new BigDecimal("100.00"),
                null,
                List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("100.00")))));

        Entry fxGain = entryFor(
                payment.getPostedTransactionId(), roleAccountId(SystemAccountRole.FX_GAIN_LOSS), EntryType.CREDIT);
        assertThat(fxGain.getBaseAmount()).isEqualByComparingTo("10.00");
        assertThat(fxGain.isFxAdjustment()).isTrue();
    }

    @Test
    void aPaymentCannotSettleInvoicesInTwoDifferentCurrenciesAtOnce() {
        fxRateService.record("EUR", new BigDecimal("1.10"), LocalDate.of(2026, 3, 25));
        fxRateService.record("GBP", new BigDecimal("1.25"), LocalDate.of(2026, 3, 25));
        Contact customer = customer();
        Invoice eurInvoice = sentInvoice(customer, "EUR", LocalDate.of(2026, 3, 25), "100.00");
        Invoice gbpInvoice = sentInvoice(customer, "GBP", LocalDate.of(2026, 3, 25), "50.00");

        assertThatThrownBy(() -> paymentService.create(new PaymentDraft(
                        customer.getId(),
                        PaymentDirection.RECEIVED,
                        LocalDate.of(2026, 3, 26),
                        new BigDecimal("150.00"),
                        null,
                        List.of(
                                new PaymentAllocationDraft(DocumentType.INVOICE, eurInvoice.getId(), new BigDecimal("100.00")),
                                new PaymentAllocationDraft(DocumentType.INVOICE, gbpInvoice.getId(), new BigDecimal("50.00"))))))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getCode()).isEqualTo("MIXED_ALLOCATION_CURRENCIES"));
    }

    @Test
    void recordingTheSameCurrencyAndDateAgainCorrectsTheRateRatherThanDuplicatingIt() {
        LocalDate asOf = LocalDate.of(2026, 5, 1);
        fxRateService.record("EUR", new BigDecimal("1.10"), asOf);
        fxRateService.record("EUR", new BigDecimal("1.12"), asOf);

        assertThat(fxRateRepository.findByCurrencyAndAsOfDate("EUR", asOf).orElseThrow().getRate())
                .isEqualByComparingTo("1.12");
        assertThat(fxRateService.rateAsOf("EUR", asOf)).isEqualByComparingTo("1.12");
    }

    @Test
    void theOrganizationsOwnBaseCurrencyCanNeverHaveARateRecordedForIt() {
        assertThatThrownBy(() -> fxRateService.record("USD", new BigDecimal("1.00"), LocalDate.of(2026, 5, 2)))
                .isInstanceOf(FxRateException.class)
                .satisfies(e -> assertThat(((FxRateException) e).getCode()).isEqualTo("CANNOT_RATE_BASE_CURRENCY"));
    }

    @Test
    void aRateAppliesFromItsOwnDateOnwardUntilANewerOneIsRecorded() {
        fxRateService.record("EUR", new BigDecimal("1.10"), LocalDate.of(2026, 5, 10));
        assertThat(fxRateService.rateAsOf("EUR", LocalDate.of(2026, 5, 10))).isEqualByComparingTo("1.10");
        assertThat(fxRateService.rateAsOf("EUR", LocalDate.of(2026, 5, 15))).isEqualByComparingTo("1.10");

        assertThatThrownBy(() -> fxRateService.rateAsOf("EUR", LocalDate.of(2026, 5, 9)))
                .isInstanceOf(FxRateException.class)
                .satisfies(e -> assertThat(((FxRateException) e).getCode()).isEqualTo("MISSING_FX_RATE"));
    }

    private Contact customer() {
        return contactService.create(new ContactDraft(
                ContactType.CUSTOMER, "Fx Test Customer " + UUID.randomUUID(), null, null, null, null, null, null,
                null, null, null, null));
    }

    private Invoice sentInvoice(Contact customer, String currency, LocalDate issueDate, String amount) {
        Invoice draft = invoiceService.create(new InvoiceDraft(
                customer.getId(),
                issueDate,
                issueDate.plusDays(30),
                null,
                List.of(new InvoiceLineDraft(null, "Consulting", BigDecimal.ONE, new BigDecimal(amount), null)),
                currency));
        return invoiceService.send(draft.getId());
    }

    private Long roleAccountId(SystemAccountRole role) {
        return accountRepository.findBySystemRole(role).orElseThrow(() -> new AssertionError("No account with role " + role)).getId();
    }

    private Entry entryFor(Long transactionId, Long accountId, EntryType type) {
        return transactionService.getEntries(transactionId).stream()
                .filter(e -> e.getAccount().getId().equals(accountId) && e.getEntryType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No %s entry to account %d on transaction %d".formatted(type, accountId, transactionId)));
    }
}
