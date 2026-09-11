package com.ledgerflow.web.dto;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record ReconciliationBatchResponse(
        Long id,
        ReconciliationStatus status,
        OffsetDateTime triggeredAt,
        OffsetDateTime completedAt,
        List<ReconciliationResultResponse> results) {

    public static ReconciliationBatchResponse from(ReconciliationBatch batch, List<ReconciliationResultResponse> results) {
        return new ReconciliationBatchResponse(
                batch.getId(), batch.getStatus(), batch.getTriggeredAt(), batch.getCompletedAt(), results);
    }
}
