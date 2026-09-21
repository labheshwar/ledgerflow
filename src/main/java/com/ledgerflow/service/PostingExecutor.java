package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.events.LedgerTopics;
import com.ledgerflow.events.TransactionPostedEvent;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.exception.ChartOfAccountsException;
import com.ledgerflow.exception.UnbalancedTransactionException;
import com.ledgerflow.money.CurrencyMismatchException;
import com.ledgerflow.money.Money;
import com.ledgerflow.outbox.OutboxRecorder;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.EntryRepository;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one posting attempt in its own transaction. Kept as a separate bean
 * (rather than a private method on PostingService) so the @Transactional
 * proxy actually applies -- calling an @Transactional method on `this`
 * from within the same class bypasses Spring's AOP proxy entirely.
 */
@Component
class PostingExecutor {

    /**
     * The accounts a foreign-currency invoice and its settlement touch.
     * Each stays nominally denominated in whatever currency it was seeded
     * or created in -- {@code Account.currency} is a default, not a
     * constraint, for exactly these roles -- because what each one holds is
     * a claim, a liability or a running total that can legitimately span
     * currencies, unlike a real bank account, which holds one currency and
     * one currency only. Bills and vendor-side accounts are deliberately
     * not here yet -- milestone 15 only reaches foreign-currency invoices.
     */
    private static final Set<SystemAccountRole> CURRENCY_FLEXIBLE_ROLES = Set.of(
            SystemAccountRole.CASH,
            SystemAccountRole.ACCOUNTS_RECEIVABLE,
            SystemAccountRole.SALES_REVENUE,
            SystemAccountRole.TAX_PAYABLE,
            SystemAccountRole.CUSTOMER_PREPAYMENTS);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EntryRepository entryRepository;
    private final OrganizationService organizationService;
    private final FxRateService fxRateService;
    private final PeriodService periodService;
    private final AuditService auditService;
    private final OutboxRecorder outboxRecorder;

    PostingExecutor(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            EntryRepository entryRepository,
            OrganizationService organizationService,
            FxRateService fxRateService,
            PeriodService periodService,
            AuditService auditService,
            OutboxRecorder outboxRecorder) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.organizationService = organizationService;
        this.fxRateService = fxRateService;
        this.periodService = periodService;
        this.auditService = auditService;
        this.outboxRecorder = outboxRecorder;
    }

    @Transactional
    Transaction execute(PostingCommand command) {
        Long orgId = TenantContext.require();
        String baseCurrency = organizationService.baseCurrency();
        // Checked before touching any account or entry: a closed period
        // means nothing in this journal should be written at all, not that
        // it should be written and then discovered invalid partway through.
        periodService.assertOpen(command.txnDate());
        Map<Long, Account> accountsById = loadAccounts(command);

        Transaction transaction = new Transaction();
        transaction.setOrgId(orgId);
        transaction.setIdempotencyKey(command.idempotencyKey());
        transaction.setDescription(command.description());
        transaction.setTxnDate(command.txnDate());
        transaction.setReversalOfTransactionId(command.reversalOfTransactionId());
        transaction = transactionRepository.save(transaction);

        List<TransactionPostedEvent.Line> eventLines = new ArrayList<>(command.entries().size());
        Money debitsBase = Money.zero(baseCurrency);
        Money creditsBase = Money.zero(baseCurrency);

        for (EntryLine line : command.entries()) {
            Account account = accountsById.get(line.accountId());
            Money amount = line.amount();
            requireAccountCurrency(account, amount);

            BigDecimal fxRate = line.rateOverride() != null
                    ? line.rateOverride()
                    : rateToBase(amount.currency(), baseCurrency, command.txnDate());
            Money baseAmount = amount.convertedTo(baseCurrency, fxRate);
            if (line.entryType() == EntryType.DEBIT) {
                debitsBase = debitsBase.plus(baseAmount);
            } else {
                creditsBase = creditsBase.plus(baseAmount);
            }

            // Nothing is written to the account row. That is the change:
            // posting used to read-modify-write a balance column guarded by
            // an optimistic lock, which made every account a contention point
            // -- and in a real business every invoice, payment and bill lands
            // on the same few accounts. Appending an entry is insert-only, so
            // two people invoicing at once no longer collide at all.
            Entry entry = new Entry();
            entry.setOrgId(orgId);
            entry.setTransaction(transaction);
            entry.setAccount(account);
            entry.setEntryType(line.entryType());
            entry.setAmount(amount.amount());
            entry.setCurrency(amount.currency());
            entry.setBaseAmount(baseAmount.amount());
            entry.setFxRate(fxRate);
            // Inferred from the role rather than carried on EntryLine: the
            // FX_GAIN_LOSS account exists for exactly this leg and nothing
            // else ever posts to it, so the role itself is already the fact.
            entry.setFxAdjustment(account.getSystemRole() == SystemAccountRole.FX_GAIN_LOSS);
            entryRepository.save(entry);

            // Records the entry, not a before/after balance. There is no
            // stored balance to snapshot any more, and computing one per
            // line would mean a query per line purely to write it down --
            // while the entry itself is already the complete and replayable
            // record of what changed.
            auditService.record(
                    "ACCOUNT", account.getId(), "ENTRY_POSTED",
                    null,
                    Map.of(
                            "transactionId", transaction.getId(),
                            "entryType", line.entryType().name(),
                            "amount", amount.amount(),
                            "currency", amount.currency()));

            eventLines.add(new TransactionPostedEvent.Line(
                    account.getId(),
                    account.getName(),
                    line.entryType().name(),
                    amount.amount(),
                    amount.currency(),
                    baseAmount.amount()));
        }

        // Only for a mixed-currency command -- PostingCommand's own
        // constructor already proved a single-currency one balances, and
        // re-deriving that from independently-rounded per-line base
        // amounts here risks rejecting a legitimate journal over sub-cent
        // rounding drift that was never actually a problem. A mixed
        // command skipped that check entirely (see PostingCommand's own
        // Javadoc), so this is the only place it is ever verified.
        if (debitsBase.compareTo(creditsBase) != 0
                && command.entries().stream().map(l -> l.amount().currency()).distinct().count() > 1) {
            throw new UnbalancedTransactionException(
                    "Debits (%s) must equal credits (%s) once converted to the reporting currency"
                            .formatted(debitsBase, creditsBase));
        }

        auditService.record(
                "TRANSACTION", transaction.getId(), "CREATE",
                null,
                Map.of("idempotencyKey", transaction.getIdempotencyKey(), "entryCount", command.entries().size()));

        // Written inside this transaction, which is the whole point: the
        // posting and the fact that it happened either both commit or
        // neither does. Publishing to Kafka here instead would be a dual
        // write, and a crash between the two would leave the ledger and the
        // event log disagreeing with no way to tell which was right.
        outboxRecorder.record(
                orgId,
                "TRANSACTION",
                String.valueOf(transaction.getId()),
                "transaction.posted",
                LedgerTopics.TRANSACTIONS,
                partitionKey(orgId),
                new TransactionPostedEvent(
                        transaction.getId(),
                        transaction.getIdempotencyKey(),
                        transaction.getDescription(),
                        transaction.getTxnDate(),
                        transaction.getCreatedAt(),
                        command.currency(),
                        baseCurrency,
                        eventLines));

        return transaction;
    }

    /**
     * An entry has to be denominated in the currency of the account it hits
     * -- unless the account's role is one of {@link #CURRENCY_FLEXIBLE_ROLES}.
     * A euro line on a dollar bank account is not a conversion, it is a
     * mistake, because that account represents a real balance held in one
     * currency; a euro line on a receivables or revenue account is a
     * perfectly ordinary foreign-currency invoice, because what that
     * account holds is a claim or a running total, not a store of value in
     * one specific currency.
     */
    private void requireAccountCurrency(Account account, Money amount) {
        if (account.getCurrency().equalsIgnoreCase(amount.currency())) {
            return;
        }
        if (CURRENCY_FLEXIBLE_ROLES.contains(account.getSystemRole())) {
            return;
        }
        throw new CurrencyMismatchException(account.getCurrency(), amount.currency());
    }

    /**
     * The identity rate needs no lookup at all; anything else comes from
     * {@link FxRateService}, which throws its own clear error if nothing
     * has ever been recorded for that currency on or before this date.
     */
    private BigDecimal rateToBase(String currency, String baseCurrency, LocalDate asOfDate) {
        if (currency.equalsIgnoreCase(baseCurrency)) {
            return BigDecimal.ONE;
        }
        return fxRateService.rateAsOf(currency, asOfDate);
    }

    /**
     * Partitioning by organization, not by account.
     *
     * Per-account would parallelise better, but a journal entry touches
     * several accounts at once and would have to be split into one event per
     * leg to be keyed that way -- destroying the only guarantee a consumer
     * actually needs, that the legs of a transaction arrive together. Per-org
     * ordering is stronger than per-account and costs nothing at the scale a
     * single business posts at.
     */
    private String partitionKey(Long orgId) {
        return "org-" + orgId;
    }

    private Map<Long, Account> loadAccounts(PostingCommand command) {
        List<Long> accountIds = command.entries().stream()
                .map(EntryLine::accountId)
                .distinct()
                .toList();

        List<Account> accounts = accountRepository.findAllById(accountIds);
        if (accounts.size() != accountIds.size()) {
            List<Long> missing = accountIds.stream()
                    .filter(id -> accounts.stream().noneMatch(a -> a.getId().equals(id)))
                    .toList();
            throw new AccountNotFoundException(missing);
        }

        accounts.forEach(this::requireCanReceiveEntries);
        return accounts.stream().collect(Collectors.toMap(Account::getId, a -> a));
    }

    /**
     * Two accounts cannot take an entry.
     *
     * A heading's balance is the sum of the accounts beneath it, so an entry
     * posted directly to one would be counted once as its own and once again
     * in the subtotal -- and the resulting balance sheet would be wrong by
     * exactly that amount, with nothing to point at.
     *
     * An archived account was deliberately taken out of use. Posting to one
     * resurrects it into reports that whoever archived it believes are closed.
     */
    private void requireCanReceiveEntries(Account account) {
        if (!account.isPostable()) {
            throw new ChartOfAccountsException(
                    "ACCOUNT_NOT_POSTABLE",
                    "%s (%s) is a heading; post to one of the accounts underneath it"
                            .formatted(account.getName(), account.getCode()));
        }
        if (account.isArchived()) {
            throw new ChartOfAccountsException(
                    "ACCOUNT_ARCHIVED",
                    "%s (%s) is archived and cannot be posted to"
                            .formatted(account.getName(), account.getCode()));
        }
    }
}
