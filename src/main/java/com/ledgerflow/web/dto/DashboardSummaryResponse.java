package com.ledgerflow.web.dto;

import com.ledgerflow.service.DashboardSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DashboardSummaryResponse(
        long totalAccounts,
        BigDecimal totalLedgerBalance,
        long postingsToday,
        BigDecimal openArTotal,
        BigDecimal overdueArTotal,
        BigDecimal openApTotal,
        BigDecimal overdueApTotal,
        OffsetDateTime updatedAt) {

    public static DashboardSummaryResponse from(DashboardSummary summary) {
        return new DashboardSummaryResponse(
                summary.totalAccounts(),
                summary.totalLedgerBalance(),
                summary.postingsToday(),
                summary.openArTotal(),
                summary.overdueArTotal(),
                summary.openApTotal(),
                summary.overdueApTotal(),
                summary.updatedAt());
    }
}
