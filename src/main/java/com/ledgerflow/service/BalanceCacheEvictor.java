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

    /**
     * Keys carry the organization because a cache hit answers before the
     * query ever reaches Postgres, and therefore before row-level security
     * has any say. Keying on account id alone would let one organization
     * read another's balance straight out of Redis.
     */
    public static String key(Long orgId, Long accountId) {
        return orgId + ":" + accountId;
    }

    public void evictAfterCommit(Long orgId, Long accountId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow(orgId, accountId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow(orgId, accountId);
            }
        });
    }

    private void evictNow(Long orgId, Long accountId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(key(orgId, accountId));
        }
    }
}
