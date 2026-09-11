package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.EntryRepository;
import com.ledgerflow.repository.TransactionRepository;
import java.math.BigDecimal;
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
    private final AuditService auditService;
    private final BalanceCacheEvictor balanceCacheEvictor;

    PostingExecutor(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            EntryRepository entryRepository,
            AuditService auditService,
            BalanceCacheEvictor balanceCacheEvictor) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.auditService = auditService;
        this.balanceCacheEvictor = balanceCacheEvictor;
    }

    @Transactional
    Transaction execute(PostingCommand command) {
        Map<Long, Account> accountsById = loadAccounts(command);

        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(command.idempotencyKey());
        transaction.setDescription(command.description());
        transaction = transactionRepository.save(transaction);

        for (EntryLine line : command.entries()) {
            Account account = accountsById.get(line.accountId());
            BigDecimal balanceBefore = account.getBalance();
            BigDecimal delta = line.entryType() == EntryType.DEBIT ? line.amount() : line.amount().negate();
            account.setBalance(balanceBefore.add(delta));

            Entry entry = new Entry();
            entry.setTransaction(transaction);
            entry.setAccount(account);
            entry.setEntryType(line.entryType());
            entry.setAmount(line.amount());
            entryRepository.save(entry);

            auditService.record(
                    "ACCOUNT", account.getId(), "BALANCE_UPDATE",
                    Map.of("balance", balanceBefore),
                    Map.of("balance", account.getBalance()));
        }

        auditService.record(
                "TRANSACTION", transaction.getId(), "CREATE",
                null,
                Map.of("idempotencyKey", transaction.getIdempotencyKey(), "entryCount", command.entries().size()));

        accountsById.keySet().forEach(balanceCacheEvictor::evictAfterCommit);

        return transaction;
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
