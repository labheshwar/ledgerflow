package com.ledgerflow.service;

import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillLine;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.TaxRate;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.BillException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.BillLineRepository;
import com.ledgerflow.repository.BillRepository;
import com.ledgerflow.repository.ContactRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bills: what a vendor says the business owes, recorded so that debt is
 * visible before any of it is paid.
 *
 * DRAFT is freely edited or deleted -- nothing outside this bill knows it
 * exists yet. Posting draws this application's own document number and
 * posts DR each line's own expense or asset account / DR Tax Receivable /
 * CR Accounts Payable as one balanced journal -- the mirror image of an
 * invoice's DR Accounts Receivable / CR Sales Revenue / CR Tax Payable,
 * because a bill is the other side of the same kind of transaction. After
 * that, a bill is exactly as immutable as any other posted transaction: it
 * can be undone by {@link ReversalService voiding}, never edited.
 *
 * {@link #post} is deliberately not {@code @Transactional} -- see {@link
 * PostingService#post}'s own Javadoc. Marking the bill OPEN (and drawing its
 * number) and recording the resulting transaction id are two separate,
 * short transactions with the actual posting in between them, exactly as
 * the project's outer-transaction-trap warning prescribes. A failure between
 * those two halves leaves a bill OPEN with no {@code posted_transaction_id};
 * {@link BillSweeper} finds and finishes those.
 */
@Service
public class BillService {

    private final BillRepository billRepository;
    private final BillLineRepository billLineRepository;
    private final ContactRepository contactRepository;
    private final ItemRepository itemRepository;
    private final TaxRateRepository taxRateRepository;
    private final ChartOfAccountsService chartOfAccounts;
    private final OrganizationService organizationService;
    private final DocumentNumberingService documentNumberingService;
    private final PostingService postingService;
    private final ReversalService reversalService;
    private final TransactionTemplate transactionTemplate;

    public BillService(
            BillRepository billRepository,
            BillLineRepository billLineRepository,
            ContactRepository contactRepository,
            ItemRepository itemRepository,
            TaxRateRepository taxRateRepository,
            ChartOfAccountsService chartOfAccounts,
            OrganizationService organizationService,
            DocumentNumberingService documentNumberingService,
            PostingService postingService,
            ReversalService reversalService,
            PlatformTransactionManager transactionManager) {
        this.billRepository = billRepository;
        this.billLineRepository = billLineRepository;
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

    public Page<Bill> search(String q, BillStatus status, Pageable pageable) {
        return billRepository.search(q, status, pageable);
    }

    public Bill get(Long id) {
        return require(id);
    }

    public List<BillLine> getLines(Long id) {
        return billLineRepository.findByBillIdOrderByLineOrderAsc(id);
    }

    /** A payment's allocation picker only ever offers a contact's own open bills. */
    public List<Bill> findOpenForContact(Long contactId) {
        return billRepository.findByContactIdAndStatusOrderByDueDateAsc(contactId, BillStatus.OPEN);
    }

    public InvoiceTotals totalsFor(List<BillLine> lines) {
        return InvoiceTotalsCalculator.compute(toCalculatorInputs(lines));
    }

    public Bill create(BillDraft draft) {
        Long orgId = TenantContext.require();
        requireVendor(draft.contactId());
        List<BillLineDraft> lines = requireLines(draft.lines());

        Bill bill = new Bill();
        bill.setOrgId(orgId);
        bill.setCurrency(organizationService.baseCurrency());
        apply(bill, draft);

        Bill saved = save(bill, null);
        saveLines(saved, lines);
        return saved;
    }

    public Bill update(Long id, BillDraft draft) {
        Bill bill = requireDraft(id);
        requireVendor(draft.contactId());
        List<BillLineDraft> lines = requireLines(draft.lines());

        apply(bill, draft);
        Bill saved = save(bill, id);
        billLineRepository.deleteByBillId(id);
        saveLines(saved, lines);
        return saved;
    }

    public void delete(Long id) {
        Bill bill = requireDraft(id);
        billLineRepository.deleteByBillId(bill.getId());
        billRepository.delete(bill);
    }

    public Bill post(Long id) {
        Bill draft = requireDraft(id);
        List<BillLine> lines = billLineRepository.findByBillIdOrderByLineOrderAsc(id);
        InvoiceTotals totals = InvoiceTotalsCalculator.compute(toCalculatorInputs(lines));
        if (totals.grandTotal().signum() <= 0) {
            throw new BillException("NOTHING_TO_BILL", "A bill with nothing owed cannot be posted");
        }

        Bill posted = transactionTemplate.execute(status -> {
            Bill fresh = require(id);
            fresh.setBillNumber(documentNumberingService.next(DocumentType.BILL));
            fresh.setStatus(BillStatus.OPEN);
            fresh.setPostedAt(OffsetDateTime.now());
            return billRepository.save(fresh);
        });

        return finishPost(posted, lines, totals);
    }

    /** Retries bills left OPEN with no posting recorded -- see this class's own Javadoc. */
    public void retryStuckPosts() {
        for (Bill bill : billRepository.findByStatusAndPostedTransactionIdIsNull(BillStatus.OPEN)) {
            List<BillLine> lines = billLineRepository.findByBillIdOrderByLineOrderAsc(bill.getId());
            InvoiceTotals totals = InvoiceTotalsCalculator.compute(toCalculatorInputs(lines));
            finishPost(bill, lines, totals);
        }
    }

    private Bill finishPost(Bill bill, List<BillLine> lines, InvoiceTotals totals) {
        String idempotencyKey = "BILL:%d:%d:POST".formatted(bill.getOrgId(), bill.getId());
        Transaction posted = postingService.post(buildPostingCommand(bill, lines, totals, idempotencyKey));

        return transactionTemplate.execute(status -> {
            Bill fresh = require(bill.getId());
            fresh.setPostedTransactionId(posted.getId());
            return billRepository.save(fresh);
        });
    }

    private PostingCommand buildPostingCommand(Bill bill, List<BillLine> lines, InvoiceTotals totals, String idempotencyKey) {
        String currency = bill.getCurrency();
        var accountsPayable = chartOfAccounts.requireByRole(SystemAccountRole.ACCOUNTS_PAYABLE);

        JournalBuilder journal = JournalBuilder.forDate(bill.getBillDate())
                .withIdempotencyKey(idempotencyKey)
                .describedAs("Bill " + bill.getBillNumber());

        for (int i = 0; i < lines.size(); i++) {
            BigDecimal lineSubtotal = totals.lines().get(i).lineSubtotal();
            if (lineSubtotal.signum() > 0) {
                journal.debit(lines.get(i).getAccountId(), Money.of(lineSubtotal, currency));
            }
        }
        if (totals.taxTotal().signum() > 0) {
            var taxReceivable = chartOfAccounts.requireByRole(SystemAccountRole.TAX_RECEIVABLE);
            journal.debit(taxReceivable.getId(), Money.of(totals.taxTotal(), currency));
        }
        journal.credit(accountsPayable.getId(), Money.of(totals.grandTotal(), currency));

        return journal.build();
    }

    public Bill voidBill(Long id, String reason) {
        Bill bill = require(id);
        if (bill.getStatus() != BillStatus.OPEN) {
            throw new BillException("BILL_NOT_VOIDABLE", "Only an open bill can be voided");
        }
        if (bill.getPostedTransactionId() == null) {
            throw new BillException("BILL_NOT_POSTED_YET", "This bill has not finished posting yet; try again shortly");
        }

        reversalService.reverse(bill.getPostedTransactionId(), LocalDate.now(), reason);

        bill.setStatus(BillStatus.VOID);
        return billRepository.save(bill);
    }

    private void apply(Bill bill, BillDraft draft) {
        bill.setContactId(draft.contactId());
        bill.setVendorReference(requireVendorReference(draft.vendorReference()));
        bill.setBillDate(requireDate(draft.billDate(), "A bill needs a bill date"));
        bill.setDueDate(requireDate(draft.dueDate(), "A bill needs a due date"));
        if (bill.getDueDate().isBefore(bill.getBillDate())) {
            throw new BillException("INVALID_DUE_DATE", "The due date cannot be before the bill date");
        }
        bill.setNotes(draft.notes() == null || draft.notes().isBlank() ? null : draft.notes().trim());
    }

    /**
     * The duplicate-vendor-bill guard. A partial unique index on (org_id,
     * contact_id, vendor_reference) WHERE status <> 'VOID' is the actual
     * enforcement -- races included -- so this is a friendlier message for
     * the common case, not the only thing standing in the way.
     */
    private Bill save(Bill bill, Long excludingId) {
        try {
            return billRepository.save(bill);
        } catch (DataIntegrityViolationException e) {
            boolean isDuplicate = billRepository.findByContactIdAndVendorReference(bill.getContactId(), bill.getVendorReference())
                    .stream()
                    .anyMatch(existing -> !existing.getId().equals(excludingId) && existing.getStatus() != BillStatus.VOID);
            if (isDuplicate) {
                throw new BillException(
                        "DUPLICATE_VENDOR_BILL",
                        "A bill with reference \"%s\" already exists for this vendor".formatted(bill.getVendorReference()));
            }
            throw e;
        }
    }

    private void saveLines(Bill bill, List<BillLineDraft> lines) {
        Map<Long, BigDecimal> taxRatesById = taxRatesById(lines);
        int order = 0;
        for (BillLineDraft line : lines) {
            BillLine entity = new BillLine();
            entity.setOrgId(bill.getOrgId());
            entity.setBillId(bill.getId());
            entity.setLineOrder(order++);
            entity.setAccountId(requireAccount(line.accountId()));
            entity.setItemId(requireItemOrNull(line.itemId()));
            entity.setDescription(requireDescription(line.description()));
            entity.setQuantity(requireQuantity(line.quantity()));
            entity.setUnitPrice(requireUnitPrice(line.unitPrice()));
            entity.setTaxRateId(requireTaxRateOrNull(line.taxRateId(), taxRatesById));
            billLineRepository.save(entity);
        }
    }

    private Map<Long, BigDecimal> taxRatesById(List<BillLineDraft> lines) {
        List<Long> ids = lines.stream().map(BillLineDraft::taxRateId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return taxRateRepository.findAllById(ids).stream().collect(Collectors.toMap(TaxRate::getId, TaxRate::getRate));
    }

    private List<InvoiceLineInput> toCalculatorInputs(List<BillLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> rates = lines.stream()
                .map(BillLine::getTaxRateId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(), id -> taxRateRepository.findById(id).map(TaxRate::getRate).orElse(BigDecimal.ZERO)));
        return lines.stream()
                .map(l -> new InvoiceLineInput(
                        l.getQuantity(), l.getUnitPrice(), l.getTaxRateId() == null ? null : rates.get(l.getTaxRateId())))
                .toList();
    }

    private void requireVendor(Long contactId) {
        Contact contact = contactRepository
                .findById(contactId)
                .orElseThrow(() -> new NoSuchElementException("No contact with id " + contactId));
        if (contact.getType() == ContactType.CUSTOMER) {
            throw new BillException(
                    "CONTACT_NOT_A_VENDOR", "%s is set up as a customer and cannot be billed".formatted(contact.getName()));
        }
    }

    private List<BillLineDraft> requireLines(List<BillLineDraft> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BillException("NO_LINES", "A bill needs at least one line");
        }
        return lines;
    }

    private Long requireAccount(Long accountId) {
        if (accountId == null) {
            throw new BillException("INVALID_LINE", "Every bill line needs an account");
        }
        return accountId;
    }

    private Long requireItemOrNull(Long itemId) {
        if (itemId == null) {
            return null;
        }
        if (!itemRepository.existsById(itemId)) {
            throw new BillException("INVALID_ITEM", "No item with id " + itemId);
        }
        return itemId;
    }

    private Long requireTaxRateOrNull(Long taxRateId, Map<Long, BigDecimal> resolved) {
        if (taxRateId == null) {
            return null;
        }
        if (!resolved.containsKey(taxRateId)) {
            throw new BillException("INVALID_TAX_RATE", "No tax rate with id " + taxRateId);
        }
        return taxRateId;
    }

    private String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new BillException("INVALID_LINE", "Every bill line needs a description");
        }
        return description.trim();
    }

    private BigDecimal requireQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BillException("INVALID_LINE", "Every bill line needs a quantity greater than zero");
        }
        return quantity;
    }

    private BigDecimal requireUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new BillException("INVALID_LINE", "A line's unit price cannot be negative");
        }
        return unitPrice;
    }

    private String requireVendorReference(String vendorReference) {
        if (vendorReference == null || vendorReference.isBlank()) {
            throw new BillException("INVALID_VENDOR_REFERENCE", "A bill needs the vendor's own bill number");
        }
        return vendorReference.trim();
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new BillException("INVALID_DATE", message);
        }
        return date;
    }

    private Bill require(Long id) {
        return billRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No bill with id " + id));
    }

    private Bill requireDraft(Long id) {
        Bill bill = require(id);
        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new BillException(
                    "BILL_NOT_EDITABLE", "Only a draft bill can be edited, posted for the first time, or deleted");
        }
        return bill;
    }
}
