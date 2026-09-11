package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.repository.AccountRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {

    private final AccountRepository accountRepository;

    public BalanceService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
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
