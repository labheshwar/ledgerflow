package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.UnbalancedTransactionException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class PostingService {

    private static final int MAX_ATTEMPTS = 3;

    private final PostingExecutor postingExecutor;

    public PostingService(PostingExecutor postingExecutor) {
        this.postingExecutor = postingExecutor;
    }

    public Transaction post(PostingCommand command) {
        validateBalanced(command);

        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return postingExecutor.execute(command);
            } catch (OptimisticLockingFailureException e) {
                lastFailure = e;
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
