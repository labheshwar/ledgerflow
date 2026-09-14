package com.ledgerflow.service;

import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.AccountWithBalance;
import com.ledgerflow.repository.EntryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Reads balances.
 *
 * Note what is gone: the Redis cache-aside that used to sit in front of this.
 * It was there because a balance read was a single-row lookup that postings
 * invalidated, and caching it looked like a win. Now that a balance is
 * derived, that argument inverts -- the read is a bounded aggregate that
 * Postgres serves from shared buffers, and caching it would buy a fraction of
 * a millisecond in exchange for an invalidation problem on every posting and
 * a second place for a cross-tenant key mistake to hide. Redis stays in the
 * stack for the work it is actually good at.
 */
@Service
public class BalanceService {

    private final AccountBalanceQueries accountBalanceQueries;
    private final EntryRepository entryRepository;

    public BalanceService(AccountBalanceQueries accountBalanceQueries, EntryRepository entryRepository) {
        this.accountBalanceQueries = accountBalanceQueries;
        this.entryRepository = entryRepository;
    }

    public AccountWithBalance getAccount(Long accountId) {
        return accountBalanceQueries
                .findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public AccountBalance getBalance(Long accountId) {
        AccountWithBalance account = getAccount(accountId);
        return new AccountBalance(account.id(), account.balance(), account.currency());
    }

    /**
     * The running balance is folded here by replaying the account in
     * accounting order -- the same DEBIT-adds/CREDIT-subtracts rule the
     * derived balance query applies in SQL. Returned newest-first, matching
     * how a statement reads.
     *
     * The last row's running balance is, by construction, the same number
     * {@link #getBalance} returns: one is the fold, the other is the sum.
     */
    public List<LedgerEntry> getLedgerEntries(Long accountId) {
        getAccount(accountId); // 404s if the account doesn't exist

        List<Entry> ascending = entryRepository.findForLedger(accountId);

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
                    entry.getTransaction().getTxnDate(),
                    entry.getCreatedAt()));
        }
        Collections.reverse(newestFirst);
        return newestFirst;
    }
}
