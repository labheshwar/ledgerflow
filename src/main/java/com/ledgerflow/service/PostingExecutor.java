package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.events.LedgerTopics;
import com.ledgerflow.events.TransactionPostedEvent;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.money.CurrencyMismatchException;
import com.ledgerflow.money.Money;
import com.ledgerflow.outbox.OutboxRecorder;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.EntryRepository;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EntryRepository entryRepository;
    private final OrganizationService organizationService;
    private final AuditService auditService;
    private final BalanceCacheEvictor balanceCacheEvictor;
    private final OutboxRecorder outboxRecorder;

    PostingExecutor(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            EntryRepository entryRepository,
            OrganizationService organizationService,
            AuditService auditService,
            BalanceCacheEvictor balanceCacheEvictor,
            OutboxRecorder outboxRecorder) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.organizationService = organizationService;
        this.auditService = auditService;
        this.balanceCacheEvictor = balanceCacheEvictor;
        this.outboxRecorder = outboxRecorder;
    }

    @Transactional
    Transaction execute(PostingCommand command) {
        Long orgId = TenantContext.require();
        String baseCurrency = organizationService.baseCurrency();
        Map<Long, Account> accountsById = loadAccounts(command);

        Transaction transaction = new Transaction();
        transaction.setOrgId(orgId);
        transaction.setIdempotencyKey(command.idempotencyKey());
        transaction.setDescription(command.description());
        transaction.setTxnDate(command.txnDate());
        transaction = transactionRepository.save(transaction);

        List<TransactionPostedEvent.Line> eventLines = new ArrayList<>(command.entries().size());

        for (EntryLine line : command.entries()) {
            Account account = accountsById.get(line.accountId());
            Money amount = line.amount();
            requireAccountCurrency(account, amount);

            BigDecimal fxRate = rateToBase(amount.currency(), baseCurrency);
            Money baseAmount = amount.convertedTo(baseCurrency, fxRate);

            BigDecimal balanceBefore = account.getBalance();
            BigDecimal delta = line.entryType() == EntryType.DEBIT
                    ? amount.amount()
                    : amount.amount().negate();
            account.setBalance(balanceBefore.add(delta));

            Entry entry = new Entry();
            entry.setOrgId(orgId);
            entry.setTransaction(transaction);
            entry.setAccount(account);
            entry.setEntryType(line.entryType());
            entry.setAmount(amount.amount());
            entry.setCurrency(amount.currency());
            entry.setBaseAmount(baseAmount.amount());
            entry.setFxRate(fxRate);
            entryRepository.save(entry);

            auditService.record(
                    "ACCOUNT", account.getId(), "BALANCE_UPDATE",
                    Map.of("balance", balanceBefore),
                    Map.of("balance", account.getBalance()));

            eventLines.add(new TransactionPostedEvent.Line(
                    account.getId(),
                    account.getName(),
                    line.entryType().name(),
                    amount.amount(),
                    amount.currency(),
                    baseAmount.amount()));
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

        accountsById.keySet().forEach(accountId -> balanceCacheEvictor.evictAfterCommit(orgId, accountId));

        return transaction;
    }

    /**
     * An entry has to be denominated in the currency of the account it hits.
     * A euro line on a dollar bank account is not a conversion, it is a
     * mistake -- the account represents a real balance held in one currency.
     */
    private void requireAccountCurrency(Account account, Money amount) {
        if (!account.getCurrency().equalsIgnoreCase(amount.currency())) {
            throw new CurrencyMismatchException(account.getCurrency(), amount.currency());
        }
    }

    /**
     * Until the rates table lands (milestone 15) the only rate available is
     * the identity one. Refusing loudly is better than defaulting to 1 and
     * quietly reporting euros as though they were dollars.
     */
    private BigDecimal rateToBase(String currency, String baseCurrency) {
        if (currency.equalsIgnoreCase(baseCurrency)) {
            return BigDecimal.ONE;
        }
        throw new UnsupportedOperationException(
                "Posting %s into an organization reporting in %s needs an exchange rate, which is not implemented yet"
                        .formatted(currency, baseCurrency));
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

        return accounts.stream().collect(Collectors.toMap(Account::getId, a -> a));
    }
}
