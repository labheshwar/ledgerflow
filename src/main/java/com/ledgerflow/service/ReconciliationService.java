package com.ledgerflow.service;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationResult;
import com.ledgerflow.domain.ReconciliationResultStatus;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.AccountWithBalance;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.ReconciliationResultRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

    private final ReconciliationBatchRepository batchRepository;
    private final ReconciliationResultRepository resultRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceQueries accountBalanceQueries;
    private final SimulatedExternalStatementFeed externalStatementFeed;

    public ReconciliationService(
            ReconciliationBatchRepository batchRepository,
            ReconciliationResultRepository resultRepository,
            AccountRepository accountRepository,
            AccountBalanceQueries accountBalanceQueries,
            SimulatedExternalStatementFeed externalStatementFeed) {
        this.batchRepository = batchRepository;
        this.resultRepository = resultRepository;
        this.accountRepository = accountRepository;
        this.accountBalanceQueries = accountBalanceQueries;
        this.externalStatementFeed = externalStatementFeed;
    }

    /**
     * Runs to completion or throws -- the caller (ReconciliationListener,
     * via the retry-then-dead-letter container factory) is responsible for
     * retrying transient failures and marking the batch FAILED once retries
     * are exhausted. This method never marks FAILED itself, so a batch
     * still being retried never misleadingly shows as failed.
     */
    @Transactional
    public void reconcile(Long batchId) {
        ReconciliationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("No reconciliation batch with id " + batchId));

        batch.setStatus(ReconciliationStatus.IN_PROGRESS);
        batchRepository.save(batch);

        // One query returns every account with its derived balance. Asking
        // per account inside the loop would be a balance query per row.
        for (AccountWithBalance account : accountBalanceQueries.findAllOrderedByName()) {
            BigDecimal ledgerBalance = account.balance();
            BigDecimal externalBalance = externalStatementFeed.fetchExternalBalance(ledgerBalance);

            ReconciliationResult result = new ReconciliationResult();
            result.setOrgId(batch.getOrgId());
            result.setBatch(batch);
            result.setAccount(accountRepository.getReferenceById(account.id()));
            result.setLedgerBalance(ledgerBalance);
            result.setExternalBalance(externalBalance);
            result.setStatus(ledgerBalance.compareTo(externalBalance) == 0
                    ? ReconciliationResultStatus.MATCHED
                    : ReconciliationResultStatus.MISMATCHED);
            resultRepository.save(result);
        }

        batch.setStatus(ReconciliationStatus.COMPLETED);
        batch.setCompletedAt(OffsetDateTime.now());
        batchRepository.save(batch);
    }
}
