package com.ledgerflow.web.dto;

import com.ledgerflow.domain.StatementLine;
import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementLineResponse(
        Long id, String externalId, LocalDate txnDate, String description, BigDecimal amount, boolean committed) {

    public static StatementLineResponse from(StatementLine line) {
        return new StatementLineResponse(
                line.getId(), line.getExternalId(), line.getTxnDate(), line.getDescription(), line.getAmount(), line.isCommitted());
    }
}
