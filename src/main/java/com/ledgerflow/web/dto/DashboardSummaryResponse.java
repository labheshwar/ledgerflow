package com.ledgerflow.web.dto;

import com.ledgerflow.service.DashboardSummary;
import java.math.BigDecimal;

public record DashboardSummaryResponse(long totalAccounts, BigDecimal totalLedgerBalance, long postingsToday) {

    public static DashboardSummaryResponse from(DashboardSummary summary) {
        return new DashboardSummaryResponse(summary.totalAccounts(), summary.totalLedgerBalance(), summary.postingsToday());
    }
}
