package com.ledgerflow.web.dto;

import com.ledgerflow.service.ReconciliationSummary;

public record ReconciliationSummaryResponse(long totalLines, long matchedLines, long unmatchedLines) {

    public static ReconciliationSummaryResponse from(ReconciliationSummary summary) {
        return new ReconciliationSummaryResponse(summary.totalLines(), summary.matchedLines(), summary.unmatchedLines());
    }
}
