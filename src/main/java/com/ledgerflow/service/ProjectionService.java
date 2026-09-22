package com.ledgerflow.service;

import com.ledgerflow.domain.AgingBucket;
import com.ledgerflow.domain.ApAgingEntry;
import com.ledgerflow.domain.ArAgingEntry;
import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.DashboardMetrics;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.events.ProcessedEventGuard;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.ApAgingRepository;
import com.ledgerflow.repository.ArAgingRepository;
import com.ledgerflow.repository.BillRepository;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.repository.DashboardMetricsRepository;
import com.ledgerflow.repository.InvoiceRepository;
import com.ledgerflow.repository.PaymentAllocationRepository;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds the read models the dashboard and the aging reports serve from.
 *
 * Every rebuild here is an absolute recompute from source (invoices, bills,
 * payment allocations, ledger entries) using the exact same totals-and-
 * balance logic {@link InvoiceService}, {@link BillService} and
 * {@link PaymentService#openDocumentsFor} already use -- never a second,
 * hand-rolled copy of tax or FX arithmetic in SQL, and never an incremental
 * "add this posting's delta to the existing row." That second property is
 * what makes a projector replay-safe: applying the same event once, twice,
 * or rebuilding from an empty table all converge on the same row, because
 * every rebuild recomputes the whole answer rather than adjusting the
 * previous one.
 */
@Service
public class ProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final InvoiceService invoiceService;
    private final BillService billService;
    private final ContactRepository contactRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ArAgingRepository arAgingRepository;
    private final ApAgingRepository apAgingRepository;
    private final DashboardMetricsRepository dashboardMetricsRepository;
    private final AccountBalanceQueries accountBalanceQueries;
    private final TransactionRepository transactionRepository;
    private final FxRateService fxRateService;
    private final OrganizationService organizationService;
    private final ProcessedEventGuard processedEventGuard;

    public ProjectionService(
            InvoiceRepository invoiceRepository,
            BillRepository billRepository,
            InvoiceService invoiceService,
            BillService billService,
            ContactRepository contactRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            ArAgingRepository arAgingRepository,
            ApAgingRepository apAgingRepository,
            DashboardMetricsRepository dashboardMetricsRepository,
            AccountBalanceQueries accountBalanceQueries,
            TransactionRepository transactionRepository,
            FxRateService fxRateService,
            OrganizationService organizationService,
            ProcessedEventGuard processedEventGuard) {
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.invoiceService = invoiceService;
        this.billService = billService;
        this.contactRepository = contactRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.arAgingRepository = arAgingRepository;
        this.apAgingRepository = apAgingRepository;
        this.dashboardMetricsRepository = dashboardMetricsRepository;
        this.accountBalanceQueries = accountBalanceQueries;
        this.transactionRepository = transactionRepository;
        this.fxRateService = fxRateService;
        this.organizationService = organizationService;
        this.processedEventGuard = processedEventGuard;
    }

    /**
     * Read-only, so a request that only ever lists what the projector last
     * wrote still goes through one well-defined transaction -- the tenant
     * GUC {@code OrgAwareJpaTransactionManager} publishes is set when a
     * transaction begins, and a repository called with no transactional
     * boundary of its own has no reliable point for that to happen at all.
     */
    @Transactional(readOnly = true)
    public List<ArAgingEntry> listArAging(Long orgId) {
        return arAgingRepository.findByOrgIdOrderByDueDateAsc(orgId);
    }

    @Transactional(readOnly = true)
    public List<ApAgingEntry> listApAging(Long orgId) {
        return apAgingRepository.findByOrgIdOrderByDueDateAsc(orgId);
    }

    /**
     * The Kafka consumer's own entry point: idempotent consumption and the
     * rebuild it guards happen in one transaction, so a crash between the
     * two can never leave {@code processed_event} claiming work that never
     * actually happened.
     *
     * @return false if this consumer group has already applied this event --
     *         the caller (the projector) should not re-publish to Redis for
     *         a redelivery nothing downstream needs to hear about twice.
     */
    @Transactional
    public boolean applyTransactionPosted(String consumerGroup, UUID eventId, Long orgId) {
        if (!processedEventGuard.markProcessed(consumerGroup, eventId, orgId)) {
            return false;
        }
        rebuildAll(orgId);
        return true;
    }

    /**
     * Recomputes all three projections for one organization in a single
     * transaction, so a reader never observes AR aging rebuilt against a
     * dashboard total that has not caught up yet.
     *
     * @return the freshly written dashboard row, for a caller (the admin
     *         rebuild endpoint) that wants to hand it straight back.
     */
    @Transactional
    public DashboardMetrics rebuildAll(Long orgId) {
        List<ArAgingEntry> arRows = rebuildArAging(orgId);
        List<ApAgingEntry> apRows = rebuildApAging(orgId);
        return rebuildDashboardMetrics(orgId, arRows, apRows);
    }

    @Transactional
    public List<ArAgingEntry> rebuildArAging(Long orgId) {
        LocalDate today = LocalDate.now();
        List<Invoice> open = invoiceRepository.findByStatusOrderByDueDateAsc(InvoiceStatus.SENT);
        Map<Long, Contact> contactCache = new HashMap<>();

        List<ArAgingEntry> rows = open.stream()
                .map(invoice -> {
                    BigDecimal balance = invoiceService
                            .totalsFor(invoiceService.getLines(invoice.getId()))
                            .grandTotal()
                            .subtract(paymentAllocationRepository.amountPaidFor(DocumentType.INVOICE, invoice.getId()));
                    if (balance.signum() <= 0) {
                        return null;
                    }
                    Contact contact = contactCache.computeIfAbsent(
                            invoice.getContactId(),
                            id -> contactRepository.findById(id).orElse(null));
                    return new ArAgingEntry(
                            orgId,
                            invoice.getId(),
                            invoice.getContactId(),
                            contact == null ? "Unknown contact" : contact.getName(),
                            invoice.getInvoiceNumber(),
                            invoice.getDueDate(),
                            invoice.getCurrency(),
                            balance,
                            AgingBucket.forDueDate(invoice.getDueDate(), today));
                })
                .filter(row -> row != null)
                .toList();

        arAgingRepository.deleteByOrgId(orgId);
        arAgingRepository.saveAll(rows);
        return rows;
    }

    @Transactional
    public List<ApAgingEntry> rebuildApAging(Long orgId) {
        LocalDate today = LocalDate.now();
        List<Bill> open = billRepository.findByStatusOrderByDueDateAsc(BillStatus.OPEN);
        Map<Long, Contact> contactCache = new HashMap<>();

        List<ApAgingEntry> rows = open.stream()
                .map(bill -> {
                    BigDecimal balance = billService
                            .totalsFor(billService.getLines(bill.getId()))
                            .grandTotal()
                            .subtract(paymentAllocationRepository.amountPaidFor(DocumentType.BILL, bill.getId()));
                    if (balance.signum() <= 0) {
                        return null;
                    }
                    Contact contact = contactCache.computeIfAbsent(
                            bill.getContactId(),
                            id -> contactRepository.findById(id).orElse(null));
                    return new ApAgingEntry(
                            orgId,
                            bill.getId(),
                            bill.getContactId(),
                            contact == null ? "Unknown contact" : contact.getName(),
                            bill.getBillNumber(),
                            bill.getDueDate(),
                            bill.getCurrency(),
                            balance,
                            AgingBucket.forDueDate(bill.getDueDate(), today));
                })
                .filter(row -> row != null)
                .toList();

        apAgingRepository.deleteByOrgId(orgId);
        apAgingRepository.saveAll(rows);
        return rows;
    }

    private DashboardMetrics rebuildDashboardMetrics(Long orgId, List<ArAgingEntry> arRows, List<ApAgingEntry> apRows) {
        BigDecimal totalLedgerBalance = accountBalanceQueries.totalBaseBalance();
        long accountCount = accountBalanceQueries.count();
        OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        long postingsToday = transactionRepository.countByCreatedAtAfter(startOfToday);

        String baseCurrency = organizationService.baseCurrency();
        LocalDate today = LocalDate.now();
        BigDecimal openAr = sumInBaseCurrency(arRows.stream(), ArAgingEntry::getCurrency, ArAgingEntry::getBalance, baseCurrency, today);
        BigDecimal overdueAr = sumInBaseCurrency(
                arRows.stream().filter(r -> r.getBucket() != AgingBucket.CURRENT),
                ArAgingEntry::getCurrency,
                ArAgingEntry::getBalance,
                baseCurrency,
                today);
        BigDecimal openAp = sumInBaseCurrency(apRows.stream(), ApAgingEntry::getCurrency, ApAgingEntry::getBalance, baseCurrency, today);
        BigDecimal overdueAp = sumInBaseCurrency(
                apRows.stream().filter(r -> r.getBucket() != AgingBucket.CURRENT),
                ApAgingEntry::getCurrency,
                ApAgingEntry::getBalance,
                baseCurrency,
                today);

        DashboardMetrics metrics =
                new DashboardMetrics(orgId, accountCount, totalLedgerBalance, postingsToday, openAr, overdueAr, openAp, overdueAp);
        return dashboardMetricsRepository.save(metrics);
    }

    /**
     * Summing raw {@code balance} across rows would add euros to dollars the
     * moment one foreign-currency invoice is open (see M15's own FX work) --
     * every row is converted to the organization's base currency, at
     * today's rate, before it goes into a dashboard total.
     *
     * A row whose currency has no rate on file as of today is excluded from
     * the total rather than failing the whole dashboard -- a future-dated
     * demo invoice with only a future-dated rate is a real, unremarkable
     * state (see FxIntegrationTest's own scope boundary on this), and a
     * report is exactly the place "show everything we can price" beats
     * "show nothing because one row couldn't be."
     */
    private <T> BigDecimal sumInBaseCurrency(
            java.util.stream.Stream<T> rows,
            java.util.function.Function<T, String> currencyOf,
            java.util.function.Function<T, BigDecimal> balanceOf,
            String baseCurrency,
            LocalDate asOfDate) {
        return rows.map(row -> {
                    String currency = currencyOf.apply(row);
                    Optional<BigDecimal> rate = fxRateService.rateAsOfIfKnown(currency, asOfDate);
                    if (rate.isEmpty()) {
                        log.warn(
                                "No {} exchange rate on or before {}; excluding one row from a dashboard total until one is recorded",
                                currency,
                                asOfDate);
                        return BigDecimal.ZERO;
                    }
                    return Money.of(balanceOf.apply(row), currency).convertedTo(baseCurrency, rate.get()).amount();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
