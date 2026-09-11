package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationResult;
import com.ledgerflow.domain.ReconciliationResultStatus;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.repository.AccountRepository;
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
    private final SimulatedExternalStatementFeed externalStatementFeed;

    public ReconciliationService(
            ReconciliationBatchRepository batchRepository,
            ReconciliationResultRepository resultRepository,
            AccountRepository accountRepository,
            SimulatedExternalStatementFeed externalStatementFeed) {
        this.batchRepository = batchRepository;
        this.resultRepository = resultRepository;
        this.accountRepository = accountRepository;
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

        for (Account account : accountRepository.findAll()) {
            BigDecimal ledgerBalance = account.getBalance();
            BigDecimal externalBalance = externalStatementFeed.fetchExternalBalance(account);

            ReconciliationResult result = new ReconciliationResult();
            result.setBatch(batch);
            result.setAccount(account);
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
