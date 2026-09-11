package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.EntryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {

    private final AccountRepository accountRepository;
    private final EntryRepository entryRepository;

    public BalanceService(AccountRepository accountRepository, EntryRepository entryRepository) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
    }

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public List<Account> listAccounts() {
        return accountRepository.findAllByOrderByNameAsc();
    }

    /**
     * The account only stores the current total, not a snapshot after each
     * entry, so the running balance is folded here by replaying every entry
     * in posting order -- the same DEBIT-adds/CREDIT-subtracts rule
     * PostingExecutor applies when it updates the live balance. Returned
     * newest-first, matching how a ledger statement reads.
     */
    public List<LedgerEntry> getLedgerEntries(Long accountId) {
        getAccount(accountId); // 404s if the account doesn't exist

        List<Entry> ascending = entryRepository.findByAccountIdOrderByCreatedAtAsc(accountId);

        BigDecimal runningBalance = BigDecimal.ZERO;
        List<LedgerEntry> newestFirst = new ArrayList<>(ascending.size());
        for (Entry entry : ascending) {
            BigDecimal delta = entry.getEntryType() == EntryType.DEBIT ? entry.getAmount() : entry.getAmount().negate();
            runningBalance = runningBalance.add(delta);
            newestFirst.add(new LedgerEntry(
                    entry.getId(),
                    entry.getTransaction().getId(),
                    entry.getEntryType(),
                    entry.getAmount(),
                    runningBalance,
                    entry.getCreatedAt()));
        }
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /**
     * Cache-aside: reads vastly outnumber postings, so this is the hot
     * path. Evicted by BalanceCacheEvictor the moment a posting commits
     * against this account -- never served stale past that point.
     */
    @Cacheable(value = BalanceCacheEvictor.CACHE_NAME, key = "#accountId")
    public AccountBalance getCachedBalance(Long accountId) {
        Account account = getAccount(accountId);
        return new AccountBalance(account.getId(), account.getBalance(), account.getCurrency());
    }
}
