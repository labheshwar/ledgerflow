package com.ledgerflow.web.dto;

import com.ledgerflow.domain.EntryType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record EntryRequest(
        @NotNull Long accountId,
        @NotNull EntryType entryType,
        @NotNull @Positive BigDecimal amount) {
}
