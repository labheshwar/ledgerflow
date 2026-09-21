package com.ledgerflow.web.dto;

import com.ledgerflow.domain.FxRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FxRateResponse(Long id, String currency, BigDecimal rate, LocalDate asOfDate, OffsetDateTime createdAt) {

    public static FxRateResponse from(FxRate fxRate) {
        return new FxRateResponse(fxRate.getId(), fxRate.getCurrency(), fxRate.getRate(), fxRate.getAsOfDate(), fxRate.getCreatedAt());
    }
}
