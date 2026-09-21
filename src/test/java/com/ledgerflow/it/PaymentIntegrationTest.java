package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.domain.PaymentStatus;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.exception.PaymentException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.BillDraft;
import com.ledgerflow.service.BillLineDraft;
import com.ledgerflow.service.BillService;
import com.ledgerflow.service.ContactDraft;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.service.InvoiceDraft;
import com.ledgerflow.service.InvoiceLineDraft;
import com.ledgerflow.service.InvoiceService;
import com.ledgerflow.service.PaymentAllocationDraft;
import com.ledgerflow.service.PaymentDraft;
import com.ledgerflow.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Payments, exercised against real Postgres. The interesting parts are the
 * ones a mocked repository could not prove: that one payment really does
 * settle three separate invoices at once and moves Cash / Accounts
 * Receivable by the exact totals involved, that an over-allocated payment
 * or one that overpays a single document is refused before anything is
 * written, and that the unallocated remainder actually lands in the
 * customer's or vendor's prepayment account.
 */
class PaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private BillService billService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionRepository transactionRepository;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void onePaymentSettlesThreeInvoicesAtOnce() {
        var ar = accountRepository.findBySystemRole(SystemAccountRole.ACCOUNTS_RECEIVABLE).orElseThrow();
        var cash = accountRepository.findBySystemRole(SystemAccountRole.CASH).orElseThrow();
        BigDecimal arBefore = balanceService.getBalance(ar.getId()).balance();
        BigDecimal cashBefore = balanceService.getBalance(cash.getId()).balance();

        Contact customer = customer();
        Invoice first = sentInvoice(customer, "100.00");
        Invoice second = sentInvoice(customer, "50.00");
        Invoice third = sentInvoice(customer, "25.00");

        Payment payment = paymentService.create(new PaymentDraft(
                customer.getId(),
                PaymentDirection.RECEIVED,
                LocalDate.of(2026, 8, 1),
                new BigDecimal("175.00"),
                null,
                List.of(
                        new PaymentAllocationDraft(DocumentType.INVOICE, first.getId(), new BigDecimal("100.00")),
                        new PaymentAllocationDraft(DocumentType.INVOICE, second.getId(), new BigDecimal("50.00")),
                        new PaymentAllocationDraft(DocumentType.INVOICE, third.getId(), new BigDecimal("25.00")))));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.POSTED);
        assertThat(payment.getPostedTransactionId()).isNotNull();

        assertThat(paymentService.amountPaidFor(DocumentType.INVOICE, first.getId())).isEqualByComparingTo("100.00");
        assertThat(paymentService.amountPaidFor(DocumentType.INVOICE, second.getId())).isEqualByComparingTo("50.00");
        assertThat(paymentService.amountPaidFor(DocumentType.INVOICE, third.getId())).isEqualByComparingTo("25.00");

        assertThat(balanceService.getBalance(cash.getId()).balance()).isEqualByComparingTo(cashBefore.add(new BigDecimal("175.00")));
        assertThat(balanceService.getBalance(ar.getId()).balance()).isEqualByComparingTo(arBefore.add(new BigDecimal("175.00")));
    }

    @Test
    void anUnallocatedRemainderPostsToThePrepaymentAccount() {
        var prepayments = accountRepository.findBySystemRole(SystemAccountRole.CUSTOMER_PREPAYMENTS).orElseThrow();
        BigDecimal before = balanceService.getBalance(prepayments.getId()).balance();

        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "40.00");

        paymentService.create(new PaymentDraft(
                customer.getId(),
                PaymentDirection.RECEIVED,
                LocalDate.of(2026, 8, 2),
                new BigDecimal("100.00"),
                null,
                List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("40.00")))));

        // 60.00 of the 100.00 was never allocated to anything.
        assertThat(balanceService.getBalance(prepayments.getId()).balance())
                .isEqualByComparingTo(before.subtract(new BigDecimal("60.00")));
    }

    @Test
    void payingABillDebitsAccountsPayableAndCreditsCash() {
        var ap = accountRepository.findBySystemRole(SystemAccountRole.ACCOUNTS_PAYABLE).orElseThrow();
        var cash = accountRepository.findBySystemRole(SystemAccountRole.CASH).orElseThrow();
        BigDecimal apBefore = balanceService.getBalance(ap.getId()).balance();
        BigDecimal cashBefore = balanceService.getBalance(cash.getId()).balance();

        Contact vendor = vendor();
        Bill bill = openBill(vendor, "60.00");

        Payment payment = paymentService.create(new PaymentDraft(
                vendor.getId(),
                PaymentDirection.PAID,
                LocalDate.of(2026, 8, 3),
                new BigDecimal("60.00"),
                null,
                List.of(new PaymentAllocationDraft(DocumentType.BILL, bill.getId(), new BigDecimal("60.00")))));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.POSTED);
        assertThat(balanceService.getBalance(ap.getId()).balance()).isEqualByComparingTo(apBefore.subtract(new BigDecimal("60.00")));
        assertThat(balanceService.getBalance(cash.getId()).balance()).isEqualByComparingTo(cashBefore.subtract(new BigDecimal("60.00")));
    }

    @Test
    void allocatingMoreThanThePaymentItselfIsRefused() {
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "100.00");

        assertThatThrownBy(() -> paymentService.create(new PaymentDraft(
                        customer.getId(),
                        PaymentDirection.RECEIVED,
                        LocalDate.of(2026, 8, 4),
                        new BigDecimal("50.00"),
                        null,
                        List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("100.00"))))))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getCode()).isEqualTo("OVER_ALLOCATED"));
    }

    @Test
    void allocatingMoreThanADocumentsOwnBalanceIsRefused() {
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "30.00");

        assertThatThrownBy(() -> paymentService.create(new PaymentDraft(
                        customer.getId(),
                        PaymentDirection.RECEIVED,
                        LocalDate.of(2026, 8, 5),
                        new BigDecimal("100.00"),
                        null,
                        List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("50.00"))))))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getCode()).isEqualTo("ALLOCATION_EXCEEDS_BALANCE"));
    }

    @Test
    void aReceivedPaymentCannotBeAllocatedToABill() {
        Contact vendor = vendor();
        Bill bill = openBill(vendor, "20.00");

        assertThatThrownBy(() -> paymentService.create(new PaymentDraft(
                        vendor.getId(),
                        PaymentDirection.RECEIVED,
                        LocalDate.of(2026, 8, 6),
                        new BigDecimal("20.00"),
                        null,
                        List.of(new PaymentAllocationDraft(DocumentType.BILL, bill.getId(), new BigDecimal("20.00"))))))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getCode()).isEqualTo("WRONG_DOCUMENT_TYPE_FOR_DIRECTION"));
    }

    @Test
    void aPaymentCannotSettleAnotherContactsDocument() {
        Contact customer = customer();
        Contact otherCustomer = customer();
        Invoice invoice = sentInvoice(otherCustomer, "20.00");

        assertThatThrownBy(() -> paymentService.create(new PaymentDraft(
                        customer.getId(),
                        PaymentDirection.RECEIVED,
                        LocalDate.of(2026, 8, 7),
                        new BigDecimal("20.00"),
                        null,
                        List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("20.00"))))))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getCode()).isEqualTo("CONTACT_MISMATCH"));
    }

    @Test
    void voidingAPaymentReversesItsPostingAndReopensTheInvoiceItSettled() {
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "80.00");

        Payment payment = paymentService.create(new PaymentDraft(
                customer.getId(),
                PaymentDirection.RECEIVED,
                LocalDate.of(2026, 8, 8),
                new BigDecimal("80.00"),
                null,
                List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("80.00")))));
        assertThat(paymentService.amountPaidFor(DocumentType.INVOICE, invoice.getId())).isEqualByComparingTo("80.00");

        Payment voided = paymentService.voidPayment(payment.getId(), "recorded in error");

        assertThat(voided.getStatus()).isEqualTo(PaymentStatus.VOID);
        assertThat(transactionRepository.findByReversalOfTransactionId(payment.getPostedTransactionId())).isPresent();
        // The voided payment's allocation no longer counts, so the invoice is owed again.
        assertThat(paymentService.amountPaidFor(DocumentType.INVOICE, invoice.getId())).isEqualByComparingTo("0.00");
    }

    @Test
    void theSweeperFinishesAPaymentStuckAfterPostingButBeforeTheWriteBack() {
        Contact customer = customer();
        Invoice invoice = sentInvoice(customer, "45.00");

        Payment payment = paymentService.create(new PaymentDraft(
                customer.getId(),
                PaymentDirection.RECEIVED,
                LocalDate.of(2026, 8, 9),
                new BigDecimal("45.00"),
                null,
                List.of(new PaymentAllocationDraft(DocumentType.INVOICE, invoice.getId(), new BigDecimal("45.00")))));
        Long realTransactionId = payment.getPostedTransactionId();

        owner.update("UPDATE payments SET posted_transaction_id = NULL WHERE id = ?", payment.getId());

        paymentService.retryStuckPosts();

        Payment healed = paymentService.get(payment.getId());
        assertThat(healed.getPostedTransactionId()).isEqualTo(realTransactionId);
    }

    private Contact customer() {
        return contactService.create(new ContactDraft(
                ContactType.CUSTOMER, "Payment Test Customer " + UUID.randomUUID(), null, null, null, null, null,
                null, null, null, null, null));
    }

    private Contact vendor() {
        return contactService.create(new ContactDraft(
                ContactType.VENDOR, "Payment Test Vendor " + UUID.randomUUID(), null, null, null, null, null, null,
                null, null, null, null));
    }

    private Invoice sentInvoice(Contact customer, String amount) {
        Invoice draft = invoiceService.create(new InvoiceDraft(
                customer.getId(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                List.of(new InvoiceLineDraft(null, "Line", BigDecimal.ONE, new BigDecimal(amount), null))));
        return invoiceService.send(draft.getId());
    }

    private Bill openBill(Contact vendor, String amount) {
        Bill draft = billService.create(new BillDraft(
                vendor.getId(),
                "VEND-" + UUID.randomUUID(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                List.of(new BillLineDraft(expenseAccount().getId(), null, "Line", BigDecimal.ONE, new BigDecimal(amount), null))));
        return billService.post(draft.getId());
    }

    private Account expenseAccount() {
        return accountRepository.findAll().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE && a.isPostable())
                .findFirst()
                .orElseThrow();
    }
}
