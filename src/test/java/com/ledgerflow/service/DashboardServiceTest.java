package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerflow.domain.DashboardMetrics;
import com.ledgerflow.repository.DashboardMetricsRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long ORG_ID = 42L;

    @Mock
    private DashboardMetricsRepository dashboardMetricsRepository;

    @Mock
    private ProjectionService projectionService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(dashboardMetricsRepository, projectionService);
        TenantContext.set(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void servesTheProjectedRowWhenOneExists() {
        DashboardMetrics metrics = new DashboardMetrics(
                ORG_ID, 3L, new BigDecimal("84.50"), 4L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO);
        when(dashboardMetricsRepository.findById(ORG_ID)).thenReturn(Optional.of(metrics));

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.totalAccounts()).isEqualTo(3);
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo("84.50");
        assertThat(summary.postingsToday()).isEqualTo(4);
        assertThat(summary.openArTotal()).isEqualByComparingTo(BigDecimal.TEN);
        verify(projectionService, never()).rebuildAll(eq(ORG_ID));
    }

    @Test
    void rebuildsOnDemandWhenNoRowExistsYet() {
        when(dashboardMetricsRepository.findById(ORG_ID)).thenReturn(Optional.empty());
        DashboardMetrics rebuilt = new DashboardMetrics(
                ORG_ID, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(projectionService.rebuildAll(ORG_ID)).thenReturn(rebuilt);

        DashboardSummary summary = dashboardService.getSummary();

        assertThat(summary.totalAccounts()).isZero();
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(projectionService).rebuildAll(ORG_ID);
    }
}
