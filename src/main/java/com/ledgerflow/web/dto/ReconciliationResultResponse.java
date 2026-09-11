package com.ledgerflow.web.dto;

import com.ledgerflow.domain.ReconciliationResult;
import com.ledgerflow.domain.ReconciliationResultStatus;
import java.math.BigDecimal;

public record ReconciliationResultResponse(
        Long accountId,
        BigDecimal ledgerBalance,
        BigDecimal externalBalance,
        ReconciliationResultStatus status) {

    public static ReconciliationResultResponse from(ReconciliationResult result) {
        return new ReconciliationResultResponse(
                result.getAccount().getId(),
                result.getLedgerBalance(),
                result.getExternalBalance(),
                result.getStatus());
    }
}
