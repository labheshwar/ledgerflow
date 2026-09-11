package com.ledgerflow.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts a cached balance only once the posting transaction that changed it
 * has actually committed. Evicting eagerly (e.g. via a plain @CacheEvict
 * alongside @Transactional) risks a concurrent reader re-populating the
 * cache with the pre-commit value in the window between eviction and
 * commit -- the read would see an uncommitted row and miss it, falling back
 * to the still-stale committed one.
 */
@Component
public class BalanceCacheEvictor {

    public static final String CACHE_NAME = "accountBalances";

    private final CacheManager cacheManager;

    public BalanceCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAfterCommit(Long accountId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow(accountId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow(accountId);
            }
        });
    }

    private void evictNow(Long accountId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(accountId);
        }
    }
}
