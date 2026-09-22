package com.ledgerflow.service;

import com.ledgerflow.domain.DashboardMetrics;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DashboardSummary(
        long totalAccounts,
        BigDecimal totalLedgerBalance,
        long postingsToday,
        BigDecimal openArTotal,
        BigDecimal overdueArTotal,
        BigDecimal openApTotal,
        BigDecimal overdueApTotal,
        OffsetDateTime updatedAt) {

    public static DashboardSummary from(DashboardMetrics metrics) {
        return new DashboardSummary(
                metrics.getAccountCount(),
                metrics.getTotalLedgerBalance(),
                metrics.getPostingsToday(),
                metrics.getOpenArTotal(),
                metrics.getOverdueArTotal(),
                metrics.getOpenApTotal(),
                metrics.getOverdueApTotal(),
                metrics.getUpdatedAt());
    }
}
