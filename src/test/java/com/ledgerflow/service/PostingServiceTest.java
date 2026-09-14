package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.domain.TransactionStatus;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.TransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    private static final String IDEMPOTENCY_KEY = "idem-key-1";

    @Mock
    private PostingExecutor postingExecutor;

    @Mock
    private TransactionRepository transactionRepository;

    private PostingService postingService;

    @BeforeEach
    void setUp() {
        postingService = new PostingService(postingExecutor, transactionRepository);
    }

    // The balance invariants moved into PostingCommand itself and are
    // covered by JournalBuilderTest. What is left here is the part
    // PostingService actually owns: idempotency and retry.

    @Test
    void postsBalancedTransaction() {
        PostingCommand command = balancedCommand();
        Transaction posted = transactionWithStatus(TransactionStatus.POSTED);
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(postingExecutor.execute(command)).thenReturn(posted);

        Transaction result = postingService.post(command);

        assertThat(result).isSameAs(posted);
        verify(postingExecutor, times(1)).execute(command);
    }

    @Test
    void idempotentReplayShortCircuitsWithoutTouchingExecutor() {
        PostingCommand command = balancedCommand();
        Transaction original = transactionWithStatus(TransactionStatus.POSTED);
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(original));

        Transaction result = postingService.post(command);

        assertThat(result).isSameAs(original);
        verify(postingExecutor, never()).execute(any());
    }

    @Test
    void retriesOnOptimisticLockConflictAndEventuallySucceeds() {
        PostingCommand command = balancedCommand();
        Transaction posted = transactionWithStatus(TransactionStatus.POSTED);
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(postingExecutor.execute(command))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenReturn(posted);

        Transaction result = postingService.post(command);

        assertThat(result).isSameAs(posted);
        verify(postingExecutor, times(3)).execute(command);
    }

    @Test
    void givesUpAfterExhaustingRetries() {
        PostingCommand command = balancedCommand();
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(postingExecutor.execute(command))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> postingService.post(command))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(postingExecutor, times(3)).execute(command);
    }

    @Test
    void concurrentDuplicateKeyReturnsWinnersResultInsteadOfFailing() {
        PostingCommand command = balancedCommand();
        Transaction winnersTransaction = transactionWithStatus(TransactionStatus.POSTED);
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnersTransaction));
        when(postingExecutor.execute(command))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Transaction result = postingService.post(command);

        assertThat(result).isSameAs(winnersTransaction);
        verify(postingExecutor, times(1)).execute(command);
        verify(transactionRepository, times(2)).findByIdempotencyKey(IDEMPOTENCY_KEY);
    }

    private static PostingCommand balancedCommand() {
        return new PostingCommand(
                IDEMPOTENCY_KEY,
                "test transaction",
                LocalDate.of(2026, 3, 14),
                List.of(
                        new EntryLine(1L, EntryType.DEBIT, Money.of("100.00", "USD")),
                        new EntryLine(2L, EntryType.CREDIT, Money.of("100.00", "USD"))));
    }

    private static Transaction transactionWithStatus(TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setIdempotencyKey(IDEMPOTENCY_KEY);
        transaction.setStatus(status);
        return transaction;
    }
}
