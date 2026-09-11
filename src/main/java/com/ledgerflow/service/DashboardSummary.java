package com.ledgerflow.service;

import com.ledgerflow.domain.ReconciliationBatch;
import java.math.BigDecimal;

public record DashboardSummary(
        long totalAccounts,
        BigDecimal totalLedgerBalance,
        long postingsToday,
        ReconciliationBatch latestReconciliation) {
}
