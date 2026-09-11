package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.TransactionRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ReconciliationBatchRepository batchRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(accountRepository, transactionRepository, batchRepository);
    }

    @Test
    void sumsAccountBalancesAndReportsLatestBatch() {
        when(accountRepository.findAll())
                .thenReturn(List.of(
                        account(new BigDecimal("100.00")),
                        account(new BigDecimal("-25.50")),
                        account(new BigDecimal("10.00"))));
        when(transactionRepository.countByCreatedAtAfter(any())).thenReturn(4L);
        ReconciliationBatch latest = batch(7L);
        when(batchRepository.findFirstByOrderByTriggeredAtDesc()).thenReturn(Optional.of(latest));

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.totalAccounts()).isEqualTo(3);
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo("84.50");
        assertThat(summary.postingsToday()).isEqualTo(4);
        assertThat(summary.latestReconciliation()).isSameAs(latest);
    }

    @Test
    void reportsZeroTotalsAndNoBatchWhenNothingExistsYet() {
        when(accountRepository.findAll()).thenReturn(List.of());
        when(transactionRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(batchRepository.findFirstByOrderByTriggeredAtDesc()).thenReturn(Optional.empty());

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.totalAccounts()).isEqualTo(0);
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.postingsToday()).isEqualTo(0);
        assertThat(summary.latestReconciliation()).isNull();
    }

    private static Account account(BigDecimal balance) {
        Account account = new Account();
        account.setBalance(balance);
        return account;
    }

    private static ReconciliationBatch batch(Long id) {
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
