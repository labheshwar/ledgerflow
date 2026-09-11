package com.ledgerflow.web.dto;

import com.ledgerflow.service.DashboardSummary;
import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryResponse(
        long totalAccounts,
        BigDecimal totalLedgerBalance,
        long postingsToday,
        ReconciliationBatchResponse latestReconciliation) {

    public static DashboardSummaryResponse from(DashboardSummary summary) {
        ReconciliationBatchResponse latest = summary.latestReconciliation() == null
                ? null
                : ReconciliationBatchResponse.from(summary.latestReconciliation(), List.of());

        return new DashboardSummaryResponse(
                summary.totalAccounts(), summary.totalLedgerBalance(), summary.postingsToday(), latest);
    }
}
