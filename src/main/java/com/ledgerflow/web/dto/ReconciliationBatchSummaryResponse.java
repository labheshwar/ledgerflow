package com.ledgerflow.web.dto;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationStatus;
import java.time.OffsetDateTime;

/**
 * The list projection of a batch: tallies instead of the full result set,
 * which is all the list view renders and which keeps the payload flat as
 * batch history grows.
 */
public record ReconciliationBatchSummaryResponse(
        Long id,
        ReconciliationStatus status,
        OffsetDateTime triggeredAt,
        OffsetDateTime completedAt,
        long accountsCompared,
        long matched,
        long mismatched) {

    public static ReconciliationBatchSummaryResponse from(ReconciliationBatch batch, long matched, long mismatched) {
        return new ReconciliationBatchSummaryResponse(
                batch.getId(),
                batch.getStatus(),
                batch.getTriggeredAt(),
                batch.getCompletedAt(),
                matched + mismatched,
                matched,
                mismatched);
    }
}
