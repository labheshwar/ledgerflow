package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.TransactionRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AccountBalanceQueries accountBalanceQueries;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ReconciliationBatchRepository batchRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(accountBalanceQueries, transactionRepository, batchRepository);
    }

    @Test
    void sumsAccountBalancesAndReportsLatestBatch() {
        when(accountBalanceQueries.count()).thenReturn(3L);
        when(accountBalanceQueries.totalBaseBalance()).thenReturn(new BigDecimal("84.50"));
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
        when(accountBalanceQueries.count()).thenReturn(0L);
        when(accountBalanceQueries.totalBaseBalance()).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(batchRepository.findFirstByOrderByTriggeredAtDesc()).thenReturn(Optional.empty());

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.totalAccounts()).isEqualTo(0);
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.postingsToday()).isEqualTo(0);
        assertThat(summary.latestReconciliation()).isNull();
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
