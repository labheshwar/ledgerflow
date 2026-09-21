package com.ledgerflow.service;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceLine;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.TaxRate;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.InvoiceException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.repository.InvoiceLineRepository;
import com.ledgerflow.repository.InvoiceRepository;
import com.ledgerflow.repository.ItemRepository;
import com.ledgerflow.repository.TaxRateRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invoices: a plan that becomes history the moment it is sent.
 *
 * DRAFT is freely edited or deleted -- nothing outside this invoice knows it
 * exists yet. Sending draws a document number and posts DR Accounts
 * Receivable / CR Sales Revenue / CR Tax Payable as one balanced journal,
 * after which the invoice is exactly as immutable as any other posted
 * transaction: it can be undone by {@link ReversalService voiding}, never
 * edited.
 *
 * {@link #send} is deliberately not {@code @Transactional} -- see
 * {@link PostingService#post}'s own Javadoc. Marking the invoice SENT (and
 * drawing its number) and recording the resulting transaction id are two
 * separate, short transactions with the actual posting in between them,
 * exactly as the project's outer-transaction-trap warning prescribes. A
 * failure between those two halves leaves an invoice SENT with no
 * {@code posted_transaction_id}; {@link InvoiceSweeper} finds and finishes
 * those.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ContactRepository contactRepository;
    private final ItemRepository itemRepository;
    private final TaxRateRepository taxRateRepository;
    private final ChartOfAccountsService chartOfAccounts;
    private final OrganizationService organizationService;
    private final DocumentNumberingService documentNumberingService;
    private final PostingService postingService;
    private final ReversalService reversalService;
    private final TransactionTemplate transactionTemplate;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            ContactRepository contactRepository,
            ItemRepository itemRepository,
            TaxRateRepository taxRateRepository,
            ChartOfAccountsService chartOfAccounts,
            OrganizationService organizationService,
            DocumentNumberingService documentNumberingService,
            PostingService postingService,
            ReversalService reversalService,
            PlatformTransactionManager transactionManager) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.contactRepository = contactRepository;
        this.itemRepository = itemRepository;
        this.taxRateRepository = taxRateRepository;
        this.chartOfAccounts = chartOfAccounts;
        this.organizationService = organizationService;
        this.documentNumberingService = documentNumberingService;
        this.postingService = postingService;
        this.reversalService = reversalService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Page<Invoice> search(String q, InvoiceStatus status, Pageable pageable) {
        return invoiceRepository.search(q, status, pageable);
    }

    public Invoice get(Long id) {
        return require(id);
    }

    public List<InvoiceLine> getLines(Long id) {
        return invoiceLineRepository.findByInvoiceIdOrderByLineOrderAsc(id);
    }

    public InvoiceTotals totalsFor(List<InvoiceLine> lines) {
        return InvoiceTotalsCalculator.compute(toCalculatorInputs(lines));
    }

    public Invoice create(InvoiceDraft draft) {
        Long orgId = TenantContext.require();
        requireCustomer(draft.contactId());
        List<InvoiceLineDraft> lines = requireLines(draft.lines());

        Invoice invoice = new Invoice();
        invoice.setOrgId(orgId);
        invoice.setCurrency(organizationService.baseCurrency());
        apply(invoice, draft);

        Invoice saved = invoiceRepository.save(invoice);
        saveLines(saved, lines);
        return saved;
    }

    public Invoice update(Long id, InvoiceDraft draft) {
        Invoice invoice = requireDraft(id);
        requireCustomer(draft.contactId());
        List<InvoiceLineDraft> lines = requireLines(draft.lines());

        apply(invoice, draft);
        Invoice saved = invoiceRepository.save(invoice);
        invoiceLineRepository.deleteByInvoiceId(id);
        saveLines(saved, lines);
        return saved;
    }

    public void delete(Long id) {
        Invoice invoice = requireDraft(id);
        invoiceLineRepository.deleteByInvoiceId(invoice.getId());
        invoiceRepository.delete(invoice);
    }

    public Invoice send(Long id) {
        Invoice draft = requireDraft(id);
        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderByLineOrderAsc(id);
        InvoiceTotals totals = InvoiceTotalsCalculator.compute(toCalculatorInputs(lines));
        if (totals.grandTotal().signum() <= 0) {
            throw new InvoiceException("NOTHING_TO_INVOICE", "An invoice with nothing to charge cannot be sent");
        }

        Invoice sent = transactionTemplate.execute(status -> {
            Invoice fresh = require(id);
            fresh.setInvoiceNumber(documentNumberingService.next(DocumentType.INVOICE));
            fresh.setStatus(InvoiceStatus.SENT);
            fresh.setSentAt(OffsetDateTime.now());
            return invoiceRepository.save(fresh);
        });

        return finishSend(sent, lines, totals);
    }

    /** Retries invoices left SENT with no posting recorded -- see this class's own Javadoc. */
    public void retryStuckSends() {
        for (Invoice invoice : invoiceRepository.findByStatusAndPostedTransactionIdIsNull(InvoiceStatus.SENT)) {
            List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderByLineOrderAsc(invoice.getId());
            InvoiceTotals totals = InvoiceTotalsCalculator.compute(toCalculatorInputs(lines));
            finishSend(invoice, lines, totals);
        }
    }

    private Invoice finishSend(Invoice invoice, List<InvoiceLine> lines, InvoiceTotals totals) {
        String idempotencyKey = "INV:%d:%d:ISSUE".formatted(invoice.getOrgId(), invoice.getId());
        Transaction posted = postingService.post(buildPostingCommand(invoice, totals, idempotencyKey));

        return transactionTemplate.execute(status -> {
            Invoice fresh = require(invoice.getId());
            fresh.setPostedTransactionId(posted.getId());
            return invoiceRepository.save(fresh);
        });
    }

    private PostingCommand buildPostingCommand(Invoice invoice, InvoiceTotals totals, String idempotencyKey) {
        String currency = invoice.getCurrency();
        var accountsReceivable = chartOfAccounts.requireByRole(SystemAccountRole.ACCOUNTS_RECEIVABLE);
        var salesRevenue = chartOfAccounts.requireByRole(SystemAccountRole.SALES_REVENUE);

        JournalBuilder journal = JournalBuilder.forDate(invoice.getIssueDate())
                .withIdempotencyKey(idempotencyKey)
                .describedAs("Invoice " + invoice.getInvoiceNumber())
                .debit(accountsReceivable.getId(), Money.of(totals.grandTotal(), currency));

        if (totals.subtotal().signum() > 0) {
            journal.credit(salesRevenue.getId(), Money.of(totals.subtotal(), currency));
        }
        if (totals.taxTotal().signum() > 0) {
            var taxPayable = chartOfAccounts.requireByRole(SystemAccountRole.TAX_PAYABLE);
            journal.credit(taxPayable.getId(), Money.of(totals.taxTotal(), currency));
        }

        return journal.build();
    }

    public Invoice voidInvoice(Long id, String reason) {
        Invoice invoice = require(id);
        if (invoice.getStatus() != InvoiceStatus.SENT) {
            throw new InvoiceException("INVOICE_NOT_VOIDABLE", "Only a sent invoice can be voided");
        }
        if (invoice.getPostedTransactionId() == null) {
            throw new InvoiceException(
                    "INVOICE_NOT_POSTED_YET", "This invoice has not finished posting yet; try again shortly");
        }

        reversalService.reverse(invoice.getPostedTransactionId(), LocalDate.now(), reason);

        invoice.setStatus(InvoiceStatus.VOID);
        return invoiceRepository.save(invoice);
    }

    private void apply(Invoice invoice, InvoiceDraft draft) {
        invoice.setContactId(draft.contactId());
        invoice.setIssueDate(requireDate(draft.issueDate(), "An invoice needs an issue date"));
        invoice.setDueDate(requireDate(draft.dueDate(), "An invoice needs a due date"));
        if (invoice.getDueDate().isBefore(invoice.getIssueDate())) {
            throw new InvoiceException("INVALID_DUE_DATE", "The due date cannot be before the issue date");
        }
        invoice.setNotes(draft.notes() == null || draft.notes().isBlank() ? null : draft.notes().trim());
    }

    private void saveLines(Invoice invoice, List<InvoiceLineDraft> lines) {
        Map<Long, BigDecimal> taxRatesById = taxRatesById(lines);
        int order = 0;
        for (InvoiceLineDraft line : lines) {
            InvoiceLine entity = new InvoiceLine();
            entity.setOrgId(invoice.getOrgId());
            entity.setInvoiceId(invoice.getId());
            entity.setLineOrder(order++);
            entity.setItemId(requireItemOrNull(line.itemId()));
            entity.setDescription(requireDescription(line.description()));
            entity.setQuantity(requireQuantity(line.quantity()));
            entity.setUnitPrice(requireUnitPrice(line.unitPrice()));
            entity.setTaxRateId(requireTaxRateOrNull(line.taxRateId(), taxRatesById));
            invoiceLineRepository.save(entity);
        }
    }

    private Map<Long, BigDecimal> taxRatesById(List<InvoiceLineDraft> lines) {
        List<Long> ids = lines.stream().map(InvoiceLineDraft::taxRateId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return taxRateRepository.findAllById(ids).stream().collect(Collectors.toMap(TaxRate::getId, TaxRate::getRate));
    }

    private List<InvoiceLineInput> toCalculatorInputs(List<InvoiceLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> rates = lines.stream()
                .map(InvoiceLine::getTaxRateId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(), id -> taxRateRepository.findById(id).map(TaxRate::getRate).orElse(BigDecimal.ZERO)));
        return lines.stream()
                .map(l -> new InvoiceLineInput(
                        l.getQuantity(), l.getUnitPrice(), l.getTaxRateId() == null ? null : rates.get(l.getTaxRateId())))
                .toList();
    }

    private void requireCustomer(Long contactId) {
        Contact contact = contactRepository
                .findById(contactId)
                .orElseThrow(() -> new NoSuchElementException("No contact with id " + contactId));
        if (contact.getType() == ContactType.VENDOR) {
            throw new InvoiceException(
                    "CONTACT_NOT_A_CUSTOMER", "%s is set up as a vendor and cannot be invoiced".formatted(contact.getName()));
        }
    }

    private List<InvoiceLineDraft> requireLines(List<InvoiceLineDraft> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InvoiceException("NO_LINES", "An invoice needs at least one line");
        }
        return lines;
    }

    private Long requireItemOrNull(Long itemId) {
        if (itemId == null) {
            return null;
        }
        if (!itemRepository.existsById(itemId)) {
            throw new InvoiceException("INVALID_ITEM", "No item with id " + itemId);
        }
        return itemId;
    }

    private Long requireTaxRateOrNull(Long taxRateId, Map<Long, BigDecimal> resolved) {
        if (taxRateId == null) {
            return null;
        }
        if (!resolved.containsKey(taxRateId)) {
            throw new InvoiceException("INVALID_TAX_RATE", "No tax rate with id " + taxRateId);
        }
        return taxRateId;
    }

    private String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvoiceException("INVALID_LINE", "Every invoice line needs a description");
        }
        return description.trim();
    }

    private BigDecimal requireQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvoiceException("INVALID_LINE", "Every invoice line needs a quantity greater than zero");
        }
        return quantity;
    }

    private BigDecimal requireUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new InvoiceException("INVALID_LINE", "A line's unit price cannot be negative");
        }
        return unitPrice;
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new InvoiceException("INVALID_DATE", message);
        }
        return date;
    }

    private Invoice require(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No invoice with id " + id));
    }

    private Invoice requireDraft(Long id) {
        Invoice invoice = require(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceException(
                    "INVOICE_NOT_EDITABLE", "Only a draft invoice can be edited, sent for the first time, or deleted");
        }
        return invoice;
    }
}
