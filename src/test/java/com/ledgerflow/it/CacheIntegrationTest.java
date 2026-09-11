package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.AccountBalance;
import com.ledgerflow.service.BalanceCacheEvictor;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.EntryLine;
import com.ledgerflow.service.PostingCommand;
import com.ledgerflow.service.PostingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Exercises the real cache-aside behavior against a real Redis instance --
 * a mocked CacheManager can't prove eviction actually happens on the wire.
 */
class CacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void balanceReadIsCachedAndEvictedTheMomentAPostingCommits() {
        Account account = new Account();
        account.setName("IT Cache Account " + UUID.randomUUID());
        account.setCurrency("USD");
        account = accountRepository.save(account);
        Long accountId = account.getId();
        String cacheKey = BalanceCacheEvictor.CACHE_NAME + "::" + accountId;

        Account counterparty = new Account();
        counterparty.setName("IT Cache Counterparty " + UUID.randomUUID());
        counterparty.setCurrency("USD");
        counterparty = accountRepository.save(counterparty);

        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        AccountBalance firstRead = balanceService.getCachedBalance(accountId);
        assertThat(firstRead.balance()).isEqualByComparingTo("0.00");
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        postingService.post(new PostingCommand(
                "it-cache-" + UUID.randomUUID(),
                "cache invalidation check",
                List.of(
                        new EntryLine(accountId, EntryType.DEBIT, new BigDecimal("30.00")),
                        new EntryLine(counterparty.getId(), EntryType.CREDIT, new BigDecimal("30.00")))));

        // The posting's own transaction has already committed by the time
        // post() returns, and eviction is registered to fire on that exact
        // commit -- so it's already happened, no polling needed.
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        AccountBalance secondRead = balanceService.getCachedBalance(accountId);
        assertThat(secondRead.balance()).isEqualByComparingTo("30.00");
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }
}
