package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.UnbalancedTransactionException;
import com.ledgerflow.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class PostingService {

    private static final int MAX_ATTEMPTS = 3;

    private final PostingExecutor postingExecutor;
    private final TransactionRepository transactionRepository;

    public PostingService(PostingExecutor postingExecutor, TransactionRepository transactionRepository) {
        this.postingExecutor = postingExecutor;
        this.transactionRepository = transactionRepository;
    }

    public Transaction post(PostingCommand command) {
        var existing = transactionRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        validateBalanced(command);

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

    private void validateBalanced(PostingCommand command) {
        List<EntryLine> entries = command.entries();
        if (entries == null || entries.size() < 2) {
            throw new UnbalancedTransactionException("A transaction needs at least two entries");
        }
        for (EntryLine entry : entries) {
            if (entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new UnbalancedTransactionException("Each entry amount must be greater than zero");
            }
        }

        BigDecimal debits = sum(entries, EntryType.DEBIT);
        BigDecimal credits = sum(entries, EntryType.CREDIT);
        if (debits.compareTo(credits) != 0) {
            throw new UnbalancedTransactionException(
                    "Debits (%s) must equal credits (%s)".formatted(debits, credits));
        }
    }

    private BigDecimal sum(List<EntryLine> entries, EntryType type) {
        return entries.stream()
                .filter(e -> e.entryType() == type)
                .map(EntryLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
