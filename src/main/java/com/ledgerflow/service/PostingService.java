package com.ledgerflow.service;

import com.ledgerflow.domain.Transaction;
import com.ledgerflow.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Posts journal entries to the ledger.
 *
 * Note what is no longer here: a balance check. A PostingCommand cannot be
 * constructed unbalanced, so by the time one arrives the invariant is already
 * established and re-checking it would only create a second place for the
 * rule to live.
 */
@Service
public class PostingService {

    private static final int MAX_ATTEMPTS = 3;

    private final PostingExecutor postingExecutor;
    private final TransactionRepository transactionRepository;

    public PostingService(PostingExecutor postingExecutor, TransactionRepository transactionRepository) {
        this.postingExecutor = postingExecutor;
        this.transactionRepository = transactionRepository;
    }

    /**
     * @throws IllegalStateException if called from inside an active
     *         transaction. Retrying on {@link OptimisticLockingFailureException}
     *         only works if each attempt gets its own transaction; a caller
     *         that wraps this call in one of its own marks that whole
     *         transaction rollback-only on the very first conflict, and
     *         every retry after it then fails silently. A document service
     *         (invoices, bills, and onward) must persist its own row in its
     *         own transaction first, call this with none active, then write
     *         the resulting transaction id back in a transaction of its own.
     */
    public Transaction post(PostingCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "PostingService.post() must not be called from inside an existing transaction -- "
                            + "see this method's own Javadoc for why, and split the caller into "
                            + "persist, post with no transaction active, then write back.");
        }

        var existing = transactionRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return postingExecutor.execute(command);
            } catch (OptimisticLockingFailureException e) {
                lastFailure = e;
            } catch (DataIntegrityViolationException e) {
                // Someone else won the race on this idempotency key between
                // our check above and this insert -- return their result
                // instead of double-posting or failing the caller.
                return transactionRepository.findByIdempotencyKey(command.idempotencyKey())
                        .orElseThrow(() -> e);
            }
        }
        throw lastFailure;
    }
}
