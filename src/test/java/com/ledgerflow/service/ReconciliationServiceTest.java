package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationResult;
import com.ledgerflow.domain.ReconciliationResultStatus;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.AccountWithBalance;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.ReconciliationResultRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private ReconciliationBatchRepository batchRepository;

    @Mock
    private ReconciliationResultRepository resultRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountBalanceQueries accountBalanceQueries;

    @Mock
    private SimulatedExternalStatementFeed externalStatementFeed;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService =
                new ReconciliationService(
                        batchRepository,
                        resultRepository,
                        accountRepository,
                        accountBalanceQueries,
                        externalStatementFeed);
    }

    @Test
    void recordsMatchedAndMismatchedResultsAndCompletesTheBatch() {
        ReconciliationBatch batch = accountBatch(1L);
        AccountWithBalance matching = account(1L, new BigDecimal("100.00"));
        AccountWithBalance drifting = account(2L, new BigDecimal("50.00"));

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(accountBalanceQueries.findAllOrderedByName()).thenReturn(List.of(matching, drifting));
        when(externalStatementFeed.fetchExternalBalance(new BigDecimal("100.00")))
                .thenReturn(new BigDecimal("100.00"));
        when(externalStatementFeed.fetchExternalBalance(new BigDecimal("50.00")))
                .thenReturn(new BigDecimal("45.00"));
        when(accountRepository.getReferenceById(anyLong())).thenAnswer(call -> {
            Account stub = new Account();
            setField(stub, "id", call.getArgument(0));
            return stub;
        });

        reconciliationService.reconcile(1L);

        ArgumentCaptor<ReconciliationResult> resultCaptor = ArgumentCaptor.forClass(ReconciliationResult.class);
        verify(resultRepository, times(2)).save(resultCaptor.capture());

        List<ReconciliationResult> savedResults = resultCaptor.getAllValues();
        ReconciliationResult matchedResult = savedResults.stream()
                .filter(r -> r.getAccount().getId().equals(1L))
                .findFirst()
                .orElseThrow();
        ReconciliationResult mismatchedResult = savedResults.stream()
                .filter(r -> r.getAccount().getId().equals(2L))
                .findFirst()
                .orElseThrow();

        assertThat(matchedResult.getStatus()).isEqualTo(ReconciliationResultStatus.MATCHED);
        assertThat(mismatchedResult.getStatus()).isEqualTo(ReconciliationResultStatus.MISMATCHED);
        assertThat(mismatchedResult.getLedgerBalance()).isEqualByComparingTo("50.00");
        assertThat(mismatchedResult.getExternalBalance()).isEqualByComparingTo("45.00");

        assertThat(batch.getStatus()).isEqualTo(ReconciliationStatus.COMPLETED);
        assertThat(batch.getCompletedAt()).isNotNull();
    }

    @Test
    void setsBatchInProgressBeforeProcessingAccounts() {
        ReconciliationBatch batch = accountBatch(1L);
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(accountBalanceQueries.findAllOrderedByName()).thenReturn(List.of());

        // ArgumentCaptor captures the object reference, not a snapshot -- since
        // `batch` is the same mutable instance saved twice, both captures would
        // reflect its *final* status. Recording the (immutable) enum value at
        // the moment of each save() call is what actually proves the ordering.
        List<ReconciliationStatus> statusesAtSaveTime = new ArrayList<>();
        when(batchRepository.save(any(ReconciliationBatch.class))).thenAnswer(invocation -> {
            ReconciliationBatch saved = invocation.getArgument(0);
            statusesAtSaveTime.add(saved.getStatus());
            return saved;
        });

        reconciliationService.reconcile(1L);

        assertThat(statusesAtSaveTime)
                .containsExactly(ReconciliationStatus.IN_PROGRESS, ReconciliationStatus.COMPLETED);
    }

    @Test
    void throwsWhenBatchDoesNotExist() {
        when(batchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reconciliationService.reconcile(99L))
                .isInstanceOf(NoSuchElementException.class);

        verify(resultRepository, times(0)).save(any());
    }

    private static AccountWithBalance account(Long id, BigDecimal balance) {
        return new AccountWithBalance(id, "Account " + id, null, "USD", balance, balance, null, null);
    }

    private static ReconciliationBatch accountBatch(Long id) {
        ReconciliationBatch batch = new ReconciliationBatch();
        setField(batch, "id", id);
        return batch;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
