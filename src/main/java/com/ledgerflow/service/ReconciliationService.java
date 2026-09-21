package com.ledgerflow.service;

import com.ledgerflow.domain.BankAccount;
import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.domain.StatementLine;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.ReconciliationException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.BankAccountRepository;
import com.ledgerflow.repository.BillRepository;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.repository.EntryRepository;
import com.ledgerflow.repository.InvoiceRepository;
import com.ledgerflow.repository.StatementLineRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Matches a bank statement's own lines against what the ledger already
 * knows, one line at a time. Every path ends the same way -- the line's
 * {@code matchedEntryId} points at the entry that now accounts for it --
 * but there are three ways to get there:
 *
 * <ul>
 * <li>{@link #matchToEntry} -- an entry that is already sitting on this
 *     bank account's own ledger account, unmatched, because something
 *     else already posted it (typically an ordinary payment).</li>
 * <li>{@link #settle} -- no entry exists yet, but an open invoice or bill
 *     does; this creates the payment that settles it, scoped to this
 *     specific bank account so the resulting entry lands where it can be
 *     matched back.</li>
 * <li>{@link #categorize} -- nothing on either side exists yet, so a new
 *     journal entry is posted directly against whichever account the
 *     line is being filed under (a bank fee, interest, and the like).</li>
 * </ul>
 *
 * {@link #settle} and {@link #categorize} both call into code that posts a
 * transaction, and neither may run inside an active transaction while
 * doing so -- see {@code PostingService#post}'s own Javadoc on the
 * outer-transaction trap. Both are therefore deliberately not {@code
 * @Transactional} themselves; the read validation ahead of the posting
 * call and the match write-back after it are each their own short,
 * separately-committed transaction, the same shape {@code PaymentService}
 * already uses for exactly this reason.
 */
@Service
public class ReconciliationService {

    /** How many days apart an entry can be from a statement line's own date and still be considered. */
    private static final int ENTRY_DATE_WINDOW_DAYS = 10;

    /** Wider than the entry window -- an invoice's due date can be weeks away from when it was actually paid. */
    private static final int DOCUMENT_DATE_WINDOW_DAYS = 45;

    private static final double MIN_DOCUMENT_SCORE = 0.15;
    private static final int MAX_SUGGESTIONS = 5;

    private final StatementLineRepository statementLineRepository;
    private final BankAccountRepository bankAccountRepository;
    private final EntryRepository entryRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final ContactRepository contactRepository;
    private final InvoiceService invoiceService;
    private final BillService billService;
    private final PaymentService paymentService;
    private final PostingService postingService;
    private final TransactionTemplate transactionTemplate;

    public ReconciliationService(
            StatementLineRepository statementLineRepository,
            BankAccountRepository bankAccountRepository,
            EntryRepository entryRepository,
            InvoiceRepository invoiceRepository,
            BillRepository billRepository,
            ContactRepository contactRepository,
            InvoiceService invoiceService,
            BillService billService,
            PaymentService paymentService,
            PostingService postingService,
            PlatformTransactionManager transactionManager) {
        this.statementLineRepository = statementLineRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.entryRepository = entryRepository;
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.contactRepository = contactRepository;
        this.invoiceService = invoiceService;
        this.billService = billService;
        this.paymentService = paymentService;
        this.postingService = postingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public ReconciliationSummary summaryFor(Long bankAccountId) {
        long total = statementLineRepository.countByBankAccountIdAndCommittedTrue(bankAccountId);
        long matched = statementLineRepository.countByBankAccountIdAndCommittedTrueAndMatchedEntryIdIsNotNull(bankAccountId);
        return new ReconciliationSummary(total, matched, total - matched);
    }

    /** {@code matched} narrows to one pane of the workspace; null returns the whole statement. */
    @Transactional(readOnly = true)
    public Page<StatementLine> lines(Long bankAccountId, Boolean matched, Pageable pageable) {
        if (matched == null) {
            return statementLineRepository.findByBankAccountIdAndCommittedOrderByTxnDateDesc(bankAccountId, true, pageable);
        }
        return matched
                ? statementLineRepository.findByBankAccountIdAndCommittedTrueAndMatchedEntryIdIsNotNullOrderByTxnDateDesc(
                        bankAccountId, pageable)
                : statementLineRepository.findByBankAccountIdAndCommittedTrueAndMatchedEntryIdIsNullOrderByTxnDateDesc(
                        bankAccountId, pageable);
    }

    @Transactional(readOnly = true)
    public List<MatchSuggestion> suggestionsFor(Long lineId) {
        StatementLine line = requireUnmatchedLine(lineId);
        BankAccount bankAccount = requireBankAccount(line.getBankAccountId());

        List<MatchSuggestion> suggestions = new ArrayList<>(entrySuggestions(line, bankAccount));
        suggestions.addAll(line.getAmount().signum() > 0 ? openInvoiceSuggestions(line) : openBillSuggestions(line));

        return suggestions.stream()
                .sorted(Comparator.comparingDouble(MatchSuggestion::score).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    @Transactional
    public StatementLine matchToEntry(Long lineId, Long entryId) {
        StatementLine line = requireUnmatchedLine(lineId);
        BankAccount bankAccount = requireBankAccount(line.getBankAccountId());

        Entry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NoSuchElementException("No entry with id " + entryId));
        if (!entry.getAccount().getId().equals(bankAccount.getAccountId())) {
            throw new ReconciliationException(
                    "ENTRY_WRONG_ACCOUNT", "That entry is not on this bank account's own ledger account");
        }
        if (statementLineRepository.existsByMatchedEntryId(entryId)) {
            throw new ReconciliationException(
                    "ENTRY_ALREADY_MATCHED", "That entry already accounts for a different statement line");
        }

        line.setMatchedEntryId(entryId);
        line.setMatchedAt(OffsetDateTime.now());
        return statementLineRepository.save(line);
    }

    /**
     * Creates the payment that settles {@code documentId}, scoped to this
     * line's own bank account, then matches the line to the entry that
     * payment posts on that account's cash side.
     */
    public StatementLine settle(Long lineId, DocumentType documentType, Long documentId) {
        StatementLine line = requireUnmatchedLine(lineId);
        BankAccount bankAccount = requireBankAccount(line.getBankAccountId());
        BigDecimal absAmount = line.getAmount().abs();
        boolean received = line.getAmount().signum() > 0;

        if (received != (documentType == DocumentType.INVOICE)) {
            throw new ReconciliationException(
                    "AMOUNT_DIRECTION_MISMATCH",
                    received ? "Money coming in can only settle an invoice" : "Money going out can only settle a bill");
        }

        Long contactId;
        BigDecimal balanceDue;
        if (documentType == DocumentType.INVOICE) {
            Invoice invoice = invoiceService.get(documentId);
            contactId = invoice.getContactId();
            balanceDue = invoiceService.totalsFor(invoiceService.getLines(documentId)).grandTotal()
                    .subtract(paymentService.amountPaidFor(DocumentType.INVOICE, documentId));
        } else {
            Bill bill = billService.get(documentId);
            contactId = bill.getContactId();
            balanceDue = billService.totalsFor(billService.getLines(documentId)).grandTotal()
                    .subtract(paymentService.amountPaidFor(DocumentType.BILL, documentId));
        }
        if (balanceDue.signum() <= 0) {
            throw new ReconciliationException("DOCUMENT_ALREADY_SETTLED", "That document has no balance left to settle");
        }

        BigDecimal allocationAmount = absAmount.min(balanceDue);
        PaymentDraft draft = new PaymentDraft(
                contactId,
                received ? PaymentDirection.RECEIVED : PaymentDirection.PAID,
                line.getTxnDate(),
                absAmount,
                "Matched from statement line #" + line.getId(),
                List.of(new PaymentAllocationDraft(documentType, documentId, allocationAmount)),
                bankAccount.getId());

        Payment payment = paymentService.create(draft);
        Entry cashEntry = cashSideEntry(payment.getPostedTransactionId(), bankAccount);
        return writeMatch(lineId, cashEntry.getId());
    }

    /** No entry and no open document either -- posts a new journal entry directly, and matches the line to it. */
    public StatementLine categorize(Long lineId, Long accountId, String description) {
        StatementLine line = requireUnmatchedLine(lineId);
        BankAccount bankAccount = requireBankAccount(line.getBankAccountId());

        String idempotencyKey = "RECON:CAT:%d:%d".formatted(line.getOrgId(), lineId);
        String journalDescription = (description == null || description.isBlank()) ? line.getDescription() : description.trim();
        String currency = bankAccount.getCurrency();

        JournalBuilder journal = JournalBuilder.forDate(line.getTxnDate())
                .withIdempotencyKey(idempotencyKey)
                .describedAs(journalDescription);
        if (line.getAmount().signum() > 0) {
            journal.debit(bankAccount.getAccountId(), Money.of(line.getAmount(), currency));
            journal.credit(accountId, Money.of(line.getAmount(), currency));
        } else {
            journal.debit(accountId, Money.of(line.getAmount().abs(), currency));
            journal.credit(bankAccount.getAccountId(), Money.of(line.getAmount().abs(), currency));
        }

        Transaction posted = postingService.post(journal.build());
        Entry cashEntry = cashSideEntry(posted.getId(), bankAccount);
        return writeMatch(lineId, cashEntry.getId());
    }

    @Transactional
    public StatementLine unmatch(Long lineId) {
        StatementLine line = statementLineRepository.findById(lineId)
                .orElseThrow(() -> new NoSuchElementException("No statement line with id " + lineId));
        if (line.getMatchedEntryId() == null) {
            throw new ReconciliationException("LINE_NOT_MATCHED", "This line is not matched to anything yet");
        }
        line.setMatchedEntryId(null);
        line.setMatchedAt(null);
        return statementLineRepository.save(line);
    }

    private Entry cashSideEntry(Long transactionId, BankAccount bankAccount) {
        return entryRepository.findByTransactionId(transactionId).stream()
                .filter(entry -> entry.getAccount().getId().equals(bankAccount.getAccountId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Posted transaction %d has no entry on bank account %d's own ledger account"
                                .formatted(transactionId, bankAccount.getId())));
    }

    /** The short, separately-committed write-back {@code settle} and {@code categorize} both finish with. */
    private StatementLine writeMatch(Long lineId, Long entryId) {
        return transactionTemplate.execute(status -> {
            StatementLine fresh = statementLineRepository.findById(lineId).orElseThrow();
            fresh.setMatchedEntryId(entryId);
            fresh.setMatchedAt(OffsetDateTime.now());
            return statementLineRepository.save(fresh);
        });
    }

    private List<MatchSuggestion> entrySuggestions(StatementLine line, BankAccount bankAccount) {
        List<Entry> candidates = entryRepository.findUnmatchedCandidates(
                bankAccount.getAccountId(),
                line.getTxnDate().minusDays(ENTRY_DATE_WINDOW_DAYS),
                line.getTxnDate().plusDays(ENTRY_DATE_WINDOW_DAYS));

        List<MatchSuggestion> suggestions = new ArrayList<>();
        for (Entry entry : candidates) {
            BigDecimal signed = entry.getEntryType() == EntryType.DEBIT ? entry.getAmount() : entry.getAmount().negate();
            if (signed.compareTo(line.getAmount()) != 0) {
                continue;
            }
            Transaction transaction = entry.getTransaction();
            double score = 0.6 * MatchScoring.descriptionSimilarity(transaction.getDescription(), line.getDescription())
                    + 0.4 * MatchScoring.dateProximity(transaction.getTxnDate(), line.getTxnDate(), ENTRY_DATE_WINDOW_DAYS);
            suggestions.add(new MatchSuggestion(
                    MatchKind.ENTRY, entry.getId(), transaction.getDescription(), transaction.getTxnDate(), signed, score));
        }
        return suggestions;
    }

    private List<MatchSuggestion> openInvoiceSuggestions(StatementLine line) {
        BigDecimal absAmount = line.getAmount().abs();
        List<MatchSuggestion> suggestions = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByStatusOrderByDueDateAsc(InvoiceStatus.SENT)) {
            BigDecimal balanceDue = invoiceService.totalsFor(invoiceService.getLines(invoice.getId())).grandTotal()
                    .subtract(paymentService.amountPaidFor(DocumentType.INVOICE, invoice.getId()));
            if (balanceDue.signum() <= 0) {
                continue;
            }
            String contactName = contactName(invoice.getContactId());
            double score = documentScore(line, absAmount, balanceDue, invoice.getDueDate(), contactName, invoice.getInvoiceNumber());
            if (score < MIN_DOCUMENT_SCORE) {
                continue;
            }
            suggestions.add(new MatchSuggestion(
                    MatchKind.INVOICE,
                    invoice.getId(),
                    "%s — %s".formatted(invoice.getInvoiceNumber(), contactName),
                    invoice.getDueDate(),
                    balanceDue,
                    score));
        }
        return suggestions;
    }

    private List<MatchSuggestion> openBillSuggestions(StatementLine line) {
        BigDecimal absAmount = line.getAmount().abs();
        List<MatchSuggestion> suggestions = new ArrayList<>();
        for (Bill bill : billRepository.findByStatusOrderByDueDateAsc(BillStatus.OPEN)) {
            BigDecimal balanceDue = billService.totalsFor(billService.getLines(bill.getId())).grandTotal()
                    .subtract(paymentService.amountPaidFor(DocumentType.BILL, bill.getId()));
            if (balanceDue.signum() <= 0) {
                continue;
            }
            String contactName = contactName(bill.getContactId());
            double score = documentScore(line, absAmount, balanceDue, bill.getDueDate(), contactName, bill.getBillNumber());
            if (score < MIN_DOCUMENT_SCORE) {
                continue;
            }
            suggestions.add(new MatchSuggestion(
                    MatchKind.BILL,
                    bill.getId(),
                    "%s — %s".formatted(bill.getBillNumber(), contactName),
                    bill.getDueDate(),
                    balanceDue.negate(),
                    score));
        }
        return suggestions;
    }

    private double documentScore(
            StatementLine line, BigDecimal absAmount, BigDecimal balanceDue, java.time.LocalDate dueDate,
            String contactName, String documentNumber) {
        double amountScore = MatchScoring.amountProximity(absAmount, balanceDue);
        double descScore = MatchScoring.descriptionSimilarity(
                line.getDescription(), contactName + " " + (documentNumber == null ? "" : documentNumber));
        double dateScore = MatchScoring.dateProximity(line.getTxnDate(), dueDate, DOCUMENT_DATE_WINDOW_DAYS);
        return 0.5 * amountScore + 0.3 * descScore + 0.2 * dateScore;
    }

    private StatementLine requireUnmatchedLine(Long lineId) {
        StatementLine line = statementLineRepository.findById(lineId)
                .orElseThrow(() -> new NoSuchElementException("No statement line with id " + lineId));
        if (!line.isCommitted()) {
            throw new ReconciliationException(
                    "LINE_NOT_COMMITTED", "This line is still a preview row, not part of the statement yet");
        }
        if (line.getMatchedEntryId() != null) {
            throw new ReconciliationException("LINE_ALREADY_MATCHED", "This line is already matched");
        }
        return line;
    }

    private BankAccount requireBankAccount(Long id) {
        return bankAccountRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No bank account with id " + id));
    }

    private String contactName(Long contactId) {
        return contactRepository.findById(contactId).map(Contact::getName).orElse("Unknown contact");
    }
}
