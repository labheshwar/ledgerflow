package com.ledgerflow.web.dto;

import com.ledgerflow.domain.AccountingPeriod;
import com.ledgerflow.domain.PeriodStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PeriodResponse(
        Long id, LocalDate startDate, LocalDate endDate, PeriodStatus status, OffsetDateTime closedAt) {

    public static PeriodResponse from(AccountingPeriod period) {
        return new PeriodResponse(
                period.getId(), period.getStartDate(), period.getEndDate(), period.getStatus(), period.getClosedAt());
    }
}
