package com.ledgerflow.service;

import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentAllocation;
import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.domain.PaymentStatus;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.PaymentException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.repository.PaymentAllocationRepository;
import com.ledgerflow.repository.PaymentRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Payments settle invoices and bills, one payment against as many of
 * either as its own amount allows.
 *
 * A payment carries no draft: recording one is recording something that
 * already happened, so {@link #create} both saves and posts it, in the
 * same two-transactions-around-the-posting-call shape {@link
 * InvoiceService#send} uses for exactly the reason the project's own
 * outer-transaction-trap warning gives -- {@link #finishPost} is what
 * {@link PaymentSweeper} retries if a crash lands between them.
 *
 * A payment can settle less than the amount it carries. The remainder
 * posts to the customer's or vendor's prepayment account instead of to any
 * one document -- money received or paid before it was earmarked for
 * anything specific still has to land somewhere real.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ContactRepository contactRepository;
    private final InvoiceService invoiceService;
    private final BillService billService;
    private final ChartOfAccountsService chartOfAccounts;
    private final OrganizationService organizationService;
    private final PostingService postingService;
    private final ReversalService reversalService;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            ContactRepository contactRepository,
            InvoiceService invoiceService,
            BillService billService,
            ChartOfAccountsService chartOfAccounts,
            OrganizationService organizationService,
            PostingService postingService,
            ReversalService reversalService,
            PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.contactRepository = contactRepository;
        this.invoiceService = invoiceService;
        this.billService = billService;
        this.chartOfAccounts = chartOfAccounts;
        this.organizationService = organizationService;
        this.postingService = postingService;
        this.reversalService = reversalService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Page<Payment> search(String q, PaymentDirection direction, Pageable pageable) {
        return paymentRepository.search(q, direction, pageable);
    }

    public Payment get(Long id) {
        return require(id);
    }

    public List<PaymentAllocation> getAllocations(Long paymentId) {
        return paymentAllocationRepository.findByPaymentId(paymentId);
    }

    /** Settled against one particular invoice or bill so far, from every payment that has not been voided. */
    public BigDecimal amountPaidFor(DocumentType documentType, Long documentId) {
        return paymentAllocationRepository.amountPaidFor(documentType, documentId);
    }

    /** Every payment that has touched one particular invoice or bill, oldest first. */
    public List<PaymentAllocation> allocationsFor(DocumentType documentType, Long documentId) {
        return paymentAllocationRepository.findByDocumentTypeAndDocumentIdOrderByIdAsc(documentType, documentId);
    }

    /** What a payment in this direction, for this contact, could still be allocated against. */
    public List<OpenDocument> openDocumentsFor(Long contactId, PaymentDirection direction) {
        if (direction == PaymentDirection.RECEIVED) {
            return invoiceService.findOpenForContact(contactId).stream()
                    .map(invoice -> new OpenDocument(
                            DocumentType.INVOICE,
                            invoice.getId(),
                            invoice.getInvoiceNumber(),
                            invoice.getDueDate(),
                            invoiceService
                                    .totalsFor(invoiceService.getLines(invoice.getId()))
                                    .grandTotal()
                                    .subtract(amountPaidFor(DocumentType.INVOICE, invoice.getId()))))
                    .filter(document -> document.balance().signum() > 0)
                    .toList();
        }
        return billService.findOpenForContact(contactId).stream()
                .map(bill -> new OpenDocument(
                        DocumentType.BILL,
                        bill.getId(),
                        bill.getBillNumber(),
                        bill.getDueDate(),
                        billService
                                .totalsFor(billService.getLines(bill.getId()))
                                .grandTotal()
                                .subtract(amountPaidFor(DocumentType.BILL, bill.getId()))))
                .filter(document -> document.balance().signum() > 0)
                .toList();
    }

    public Payment create(PaymentDraft draft) {
        Long orgId = TenantContext.require();
        Contact contact = requireContactFor(draft.contactId(), draft.direction());
        BigDecimal amount = requireAmount(draft.amount());
        LocalDate paymentDate = requireDate(draft.paymentDate());
        List<PaymentAllocationDraft> allocations = draft.allocations() == null ? List.of() : draft.allocations();

        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (PaymentAllocationDraft allocation : allocations) {
            requireMatchingDocumentType(allocation.documentType(), draft.direction());
            BigDecimal remaining = remainingBalance(allocation.documentType(), allocation.documentId(), draft.contactId());
            BigDecimal allocationAmount = requireAmount(allocation.amount());
            if (allocationAmount.compareTo(remaining) > 0) {
                throw new PaymentException(
                        "ALLOCATION_EXCEEDS_BALANCE",
                        "Cannot allocate %s to %s #%d, which only has %s outstanding"
                                .formatted(allocationAmount, allocation.documentType(), allocation.documentId(), remaining));
            }
            allocatedTotal = allocatedTotal.add(allocationAmount);
        }
        if (allocatedTotal.compareTo(amount) > 0) {
            throw new PaymentException(
                    "OVER_ALLOCATED", "Allocations total %s, more than the payment's own %s".formatted(allocatedTotal, amount));
        }

        Payment saved = transactionTemplate.execute(status -> {
            Payment payment = new Payment();
            payment.setOrgId(orgId);
            payment.setContactId(contact.getId());
            payment.setDirection(draft.direction());
            payment.setPaymentDate(paymentDate);
            payment.setAmount(amount);
            payment.setCurrency(organizationService.baseCurrency());
            payment.setNotes(draft.notes() == null || draft.notes().isBlank() ? null : draft.notes().trim());
            Payment persisted = paymentRepository.save(payment);

            for (PaymentAllocationDraft allocation : allocations) {
                PaymentAllocation entity = new PaymentAllocation();
                entity.setOrgId(orgId);
                entity.setPaymentId(persisted.getId());
                entity.setDocumentType(allocation.documentType());
                entity.setDocumentId(allocation.documentId());
                entity.setAmount(allocation.amount());
                paymentAllocationRepository.save(entity);
            }
            return persisted;
        });

        return finishPost(saved, contact.getName());
    }

    /** Retries payments left POSTED with no posting recorded -- see this class's own Javadoc. */
    public void retryStuckPosts() {
        for (Payment payment : paymentRepository.findByStatusAndPostedTransactionIdIsNull(PaymentStatus.POSTED)) {
            String contactName = contactRepository.findById(payment.getContactId()).map(Contact::getName).orElse("contact");
            finishPost(payment, contactName);
        }
    }

    private Payment finishPost(Payment payment, String contactName) {
        BigDecimal allocatedTotal = paymentAllocationRepository.findByPaymentId(payment.getId()).stream()
                .map(PaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String idempotencyKey = "PMT:%d:%d:POST".formatted(payment.getOrgId(), payment.getId());
        Transaction posted =
                postingService.post(buildPostingCommand(payment, allocatedTotal, contactName, idempotencyKey));

        return transactionTemplate.execute(status -> {
            Payment fresh = require(payment.getId());
            fresh.setPostedTransactionId(posted.getId());
            return paymentRepository.save(fresh);
        });
    }

    private PostingCommand buildPostingCommand(Payment payment, BigDecimal allocatedTotal, String contactName, String idempotencyKey) {
        String currency = payment.getCurrency();
        BigDecimal excess = payment.getAmount().subtract(allocatedTotal);
        var cash = chartOfAccounts.requireByRole(SystemAccountRole.CASH);

        boolean received = payment.getDirection() == PaymentDirection.RECEIVED;
        JournalBuilder journal = JournalBuilder.forDate(payment.getPaymentDate())
                .withIdempotencyKey(idempotencyKey)
                .describedAs((received ? "Payment received from " : "Payment sent to ") + contactName);

        if (received) {
            journal.debit(cash.getId(), Money.of(payment.getAmount(), currency));
            if (allocatedTotal.signum() > 0) {
                var ar = chartOfAccounts.requireByRole(SystemAccountRole.ACCOUNTS_RECEIVABLE);
                journal.credit(ar.getId(), Money.of(allocatedTotal, currency));
            }
            if (excess.signum() > 0) {
                var prepayments = chartOfAccounts.requireByRole(SystemAccountRole.CUSTOMER_PREPAYMENTS);
                journal.credit(prepayments.getId(), Money.of(excess, currency));
            }
        } else {
            if (allocatedTotal.signum() > 0) {
                var ap = chartOfAccounts.requireByRole(SystemAccountRole.ACCOUNTS_PAYABLE);
                journal.debit(ap.getId(), Money.of(allocatedTotal, currency));
            }
            if (excess.signum() > 0) {
                var prepayments = chartOfAccounts.requireByRole(SystemAccountRole.VENDOR_PREPAYMENTS);
                journal.debit(prepayments.getId(), Money.of(excess, currency));
            }
            journal.credit(cash.getId(), Money.of(payment.getAmount(), currency));
        }
        return journal.build();
    }

    public Payment voidPayment(Long id, String reason) {
        Payment payment = require(id);
        if (payment.getStatus() != PaymentStatus.POSTED) {
            throw new PaymentException("PAYMENT_NOT_VOIDABLE", "Only a posted payment can be voided");
        }
        if (payment.getPostedTransactionId() == null) {
            throw new PaymentException("PAYMENT_NOT_POSTED_YET", "This payment has not finished posting yet; try again shortly");
        }

        reversalService.reverse(payment.getPostedTransactionId(), LocalDate.now(), reason);

        payment.setStatus(PaymentStatus.VOID);
        return paymentRepository.save(payment);
    }

    private BigDecimal remainingBalance(DocumentType documentType, Long documentId, Long expectedContactId) {
        BigDecimal grandTotal;
        Long contactId;
        if (documentType == DocumentType.INVOICE) {
            Invoice invoice = invoiceService.get(documentId);
            if (invoice.getStatus() != InvoiceStatus.SENT) {
                throw new PaymentException("DOCUMENT_NOT_OPEN", "Invoice #%d is not open to be paid against".formatted(documentId));
            }
            contactId = invoice.getContactId();
            grandTotal = invoiceService.totalsFor(invoiceService.getLines(documentId)).grandTotal();
        } else {
            Bill bill = billService.get(documentId);
            if (bill.getStatus() != BillStatus.OPEN) {
                throw new PaymentException("DOCUMENT_NOT_OPEN", "Bill #%d is not open to be paid against".formatted(documentId));
            }
            contactId = bill.getContactId();
            grandTotal = billService.totalsFor(billService.getLines(documentId)).grandTotal();
        }
        if (!contactId.equals(expectedContactId)) {
            throw new PaymentException(
                    "CONTACT_MISMATCH", "%s #%d does not belong to this payment's contact".formatted(documentType, documentId));
        }
        BigDecimal alreadyPaid = paymentAllocationRepository.amountPaidFor(documentType, documentId);
        return grandTotal.subtract(alreadyPaid);
    }

    private void requireMatchingDocumentType(DocumentType documentType, PaymentDirection direction) {
        boolean matches = (direction == PaymentDirection.RECEIVED) == (documentType == DocumentType.INVOICE);
        if (!matches) {
            throw new PaymentException(
                    "WRONG_DOCUMENT_TYPE_FOR_DIRECTION",
                    direction == PaymentDirection.RECEIVED
                            ? "A received payment can only be allocated to invoices"
                            : "A payment sent can only be allocated to bills");
        }
    }

    private Contact requireContactFor(Long contactId, PaymentDirection direction) {
        Contact contact = contactRepository
                .findById(contactId)
                .orElseThrow(() -> new NoSuchElementException("No contact with id " + contactId));
        if (direction == PaymentDirection.RECEIVED && contact.getType() == ContactType.VENDOR) {
            throw new PaymentException(
                    "CONTACT_NOT_A_CUSTOMER", "%s is set up as a vendor and cannot make a payment to you".formatted(contact.getName()));
        }
        if (direction == PaymentDirection.PAID && contact.getType() == ContactType.CUSTOMER) {
            throw new PaymentException(
                    "CONTACT_NOT_A_VENDOR", "%s is set up as a customer and cannot be paid as a vendor".formatted(contact.getName()));
        }
        return contact;
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentException("INVALID_AMOUNT", "A payment amount must be greater than zero");
        }
        return amount;
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new PaymentException("INVALID_DATE", "A payment needs a date");
        }
        return date;
    }

    private Payment require(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No payment with id " + id));
    }
}
