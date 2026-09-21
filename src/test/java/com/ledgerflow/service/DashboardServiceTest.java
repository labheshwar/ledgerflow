package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.TransactionRepository;
import java.math.BigDecimal;
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

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(accountBalanceQueries, transactionRepository);
    }

    @Test
    void sumsAccountBalancesAndTodaysPostings() {
        when(accountBalanceQueries.count()).thenReturn(3L);
        when(accountBalanceQueries.totalBaseBalance()).thenReturn(new BigDecimal("84.50"));
        when(transactionRepository.countByCreatedAtAfter(any())).thenReturn(4L);

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.totalAccounts()).isEqualTo(3);
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo("84.50");
        assertThat(summary.postingsToday()).isEqualTo(4);
    }

    @Test
    void reportsZeroTotalsWhenNothingExistsYet() {
        when(accountBalanceQueries.count()).thenReturn(0L);
        when(accountBalanceQueries.totalBaseBalance()).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countByCreatedAtAfter(any())).thenReturn(0L);

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.totalAccounts()).isEqualTo(0);
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.postingsToday()).isEqualTo(0);
    }
}
